package com.twitter

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{Flow, Framing}
import akka.stream.{ActorAttributes, ActorMaterializer, Supervision}
import akka.util.ByteString
import com.hunorkovacs.koauth.domain.KoauthRequest
import com.hunorkovacs.koauth.service.consumer.DefaultConsumerService
import com.modules.{ActorModuleImpl, ConfigurationModuleImpl, PersistenceModuleImpl}
import com.twitter.models.{Country, Location, Tweet}
import com.typesafe.config.ConfigFactory
import net.reduls.igo.Tagger
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object StreamingWordsOfKey extends App {

  val modules = new ConfigurationModuleImpl with ActorModuleImpl with PersistenceModuleImpl
  val conf = modules.config
  val locationsConf = ConfigFactory.load(this.getClass().getClassLoader(), "locations.conf")

  //Get your credentials from https://apps.twitter.com and replace the values below
  private val consumerKey = conf.getString("twitter.consumerKey")
  private val consumerSecret = conf.getString("twitter.consumerSecret")
  private val accessToken = conf.getString("twitter.accessToken")
  private val accessTokenSecret = conf.getString("twitter.accessTokenSecret")
  private val url = "https://stream.twitter.com/1.1/statuses/filter.json"

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val formats = DefaultFormats
  //	implicit val ec= system.dispatcher

  private val consumer = new DefaultConsumerService(system.dispatcher)
  val serial = SerializationExtension(system).findSerializerFor(Tweet)
  modules.dictionaryDal.createTable()
  modules.scoreDal.createTable()

  val prefs: List[Location] = {
    val list = locationsConf.getConfigList("locations.prefs")
    list.map { loc =>
      Location(loc.getString("id").toLong, loc.getString("name"), loc.getString("text"), loc.getStringList("longitude").toList, loc.getStringList("latitude").toList, loc.getStringList("capital").toList)
    }.toList
  }

  val countries: List[Country] = {
    val countries = locationsConf.getConfigList("locations.countries")
    countries.map { ctry =>
      Country(ctry.getString("country_code"), ctry.getString("country"))
    }.toList
  }

  val track = "london"
  val prefId = 27
  val targetPref = prefs(prefId - 1)
  val locations = targetPref.longitude(0) + "," + targetPref.latitude(0) + "," + targetPref.longitude(1) + "," + targetPref.latitude(1)
  val body = "track=" + track
  //  val body = "track=" + track + "&locations=" + locations
  val uri_source = Uri(url)

  val tagger = new Tagger("lib/ipadic")
  val enableName = List("名詞", "動名詞")

  //Create Oauth 1a header
  val oauthHeader: Future[String] = consumer.createOauthenticatedRequest(
    KoauthRequest(
      method = "POST",
      url = url,
      authorizationHeader = None,
      body = Some(body)
    ), consumerKey, consumerSecret, accessToken, accessTokenSecret
  ) map (_.header)

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

      val httpRequest: HttpRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = uri_source,
        headers = httpHeaders,
        //entity = FormData(("locations", locations)).toEntity
        //entity = FormData(("track", track), ("locations", locations)).toEntity
        entity = FormData(("track", track)).toEntity
      )
      val req = Http().singleRequest(httpRequest)
      req.onComplete {
        case Success(res) if res.status.isSuccess() =>
          println("success!")
          println(res.status)
          res.entity.dataBytes.via(chunkBuffer).via(flow).via(flow_serialize).runForeach(x => println(x))
        case Success(res) => println("http BAD status"); println(res.status)
        case Failure(res) => println("We potato'd"); println(res)
      }
    case Failure(failure) => println(failure.getMessage)
  }

  val chunkBuffer = Framing.delimiter(ByteString("\r\n"), 25001, false)
    .map(_.utf8String)

  import org.json4s.JsonDSL._

  val flow = Flow[String].map { json => {
    parse(json).transformField {
      case JField("user", u) => ("user_id", compact(render(u \ "id_str")))
      //case JField("user", u) => ("user_id", compact(render(u \ "lang")))
    }
  }.extract[Tweet]
  }.withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))

  val flow_serialize = Flow[Tweet].map(tw => println(tw.text))

}