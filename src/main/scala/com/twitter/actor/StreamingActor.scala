package com.twitter.actor

import java.sql.Timestamp
import java.util.Date

import akka.actor.{Actor, ActorSystem, Props, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{Flow, Framing, Sink}
import akka.stream.{ActorAttributes, ActorMaterializer, Supervision}
import akka.util.ByteString
import com.hunorkovacs.koauth.domain.KoauthRequest
import com.hunorkovacs.koauth.service.consumer.DefaultConsumerService
import com.igo.Word
import com.modules.{ActorModuleImpl, ConfigurationModuleImpl, PersistenceModuleImpl}
import com.twitter.entities.Dictionary
import com.twitter.models.{Country, Location, Tweet}
import com.typesafe.config.ConfigFactory
import net.reduls.igo.Tagger
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.{Failure, Success}

class StreamingActor extends Actor {

  import StreamingActor._

  override def receive: Receive = {
    case startId: Int => stream(Option(startId))

    case Terminated(self) =>
      println("Terminated!")
      context.stop(self)
  }

}

object StreamingActor {
  val modules = new ConfigurationModuleImpl with ActorModuleImpl with PersistenceModuleImpl
  val conf = modules.config
  val locationsConf = ConfigFactory.load(this.getClass().getClassLoader(), "locations.conf")

  //Get your credentials from https://apps.twitter.com and replace the values below
  private val consumerKey = conf.getString("twitter.consumerKey")
  private val consumerSecret = conf.getString("twitter.consumerSecret")
  private val accessToken = conf.getString("twitter.accessToken")
  private val accessTokenSecret = conf.getString("twitter.accessTokenSecret")
  private val url = "https://stream.twitter.com/1.1/statuses/filter.json"

  implicit val system = ActorSystem("StreamingActor")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  def props = Props[StreamingActor]

  private val consumer = new DefaultConsumerService(system.dispatcher)
  val serial = SerializationExtension(system).findSerializerFor(Tweet)
  implicit val formats = DefaultFormats
  modules.dictionaryDal.createTable()
  modules.scoreDal.createTable()

  val prefs: List[Location] = {
    val list = locationsConf.getConfigList("locations.prefs")
    list.map { loc =>
      Location(loc.getString("id").toLong, loc.getString("name"), loc.getString("text"),
        loc.getStringList("longitude").toList, loc.getStringList("latitude").toList, loc.getStringList("capital").toList)
    }.toList
  }

  val countries: List[Country] = {
    val countries = locationsConf.getConfigList("locations.countries")
    countries.map { ctry =>
      Country(ctry.getString("country_code"), ctry.getString("country"))
    }.toList
  }

  val tagger = new Tagger("lib/ipadic")
  val enableName = List("名詞", "動名詞")
  private var increment = 0

  def stream(startId: Option[Int]) = {
    val initialId: Int = startId match {
      case Some(n) if (n == 0) => n + 1
      case Some(s) => s
      case None => 1
    }
    increment += 1

    println("prefId ========> " + (initialId + increment - 1))
    val targetPref = prefs(initialId + increment - 2)
    val locations = targetPref.longitude(0) + "," + targetPref.latitude(0) + "," + targetPref.longitude(1) + "," + targetPref.latitude(1)
    val body = "locations=" + locations
    val uri_source = Uri(url)

    val oauthHeader: Future[String] = consumer.createOauthenticatedRequest(
      KoauthRequest(
        method = "POST",
        url = url,
        authorizationHeader = None,
        body = Some(body)
      ), consumerKey, consumerSecret, accessToken, accessTokenSecret
    ) map (_.header)

    val chunkBuffer = Framing.delimiter(ByteString("\r\n"), 25001, false)
      .map(_.utf8String)

    oauthHeader.onComplete {
      case Success(header) =>
        val httpHeaders: List[HttpHeader] = List(
          HttpHeader.parse("Authorization", header) match {
            case ParsingResult.Ok(h, _) => Some(h)
            case _ => None
          },
          HttpHeader.parse("Accept", "*/*") match {
            case ParsingResult.Ok(h, _) => Some(h)
            case _ => None
          }
        ).flatten

        val httpRequest: HttpRequest = HttpRequest(method = HttpMethods.POST, uri = uri_source,
          headers = httpHeaders, entity = FormData(("locations", locations)).toEntity)

        Http().singleRequest(httpRequest).onComplete {
          case Success(res) if res.status.isSuccess() =>
            println("Success! : " + res.status)
            res.entity.dataBytes.via(chunkBuffer).via(flow).via(flow_serialize).runWith(Sink.ignore)
          case Success(res) =>
            println("http BAD status status: " + res.status + " entity: " + res.entity)
          case Failure(res) => println(res.getMessage)
        }
      case Failure(failure) => println(failure.getMessage)
    }

    import org.json4s.JsonDSL._

    def flow = Flow[String].map { json => {
      parse(json).transformField {
        case JField("user", u) => ("user_id", compact(render(u \ "id_str")))
      }
    }.extract[Tweet]
    }.withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))

    def flow_serialize = Flow[Tweet].map { tw =>
      def insertWords(tw: Tweet, words: List[Option[Word]]) = {
        for (elem <- words) {
          elem match {
            case Some(wd) => {
              val timestamp = new Timestamp(new Date().getTime())
              modules.dictionaryDal.insert(Dictionary(0L, Option(wd.word), Option(wd.wordType), tw.lang,
                Option(countries.head.country_code), Option(countries.head.country), Option(targetPref.id), Option(targetPref.text), Option(""), Option(timestamp), Option(timestamp)))
            }
            case None => println("None.")
          }
        }
        words
      }

      calc(tw).onComplete {
        case Success(words) => insertWords(tw, words)
        case Failure(t) => println(t.getMessage)
      }
    }

    def calc(tw: Tweet): Future[List[Option[Word]]] = {
      import scala.collection.JavaConversions._

      val parsedList = Word.combine(tagger.parse(tw.text).map(m => Word(m.surface, m.feature)))
      val sentences = Word.splitBySymbol(parsedList.filter(_.subType != "代名詞"))
      Future.successful(sentences.filter(word => word match {
        case Some(w) if (enableName.contains(w.wordType) && w.subType != "非自立") =>
          println(w.wordType + ": " + w.word)
          true
        case _ => false
      }))
    }

  }

}

