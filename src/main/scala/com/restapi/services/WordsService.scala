package com.restapi.services

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.modules.{ActorModuleImpl, ConfigurationModuleImpl, PersistenceModuleImpl}
import com.twitter.entities.{Dictionaries, Scores}
import com.twitter.models.{Location, Marker, Word}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

trait WordsService {
  protected val locationsConf = ConfigFactory.load(this.getClass().getClassLoader(), "locations.conf")
  protected val modules = new ConfigurationModuleImpl with ActorModuleImpl with PersistenceModuleImpl
  protected val dictionaryDal = modules.dictionaryDal
  protected val scoreDal = modules.scoreDal

  protected val dictionaryTable = TableQuery[Dictionaries]
  protected val scoreTable = TableQuery[Scores]
  protected val prefLocations: List[Location] = {
    val list = locationsConf.getConfigList("locations.prefs").asScala
    list.map { loc =>
      Location(loc.getString("id").toLong, loc.getString("name"), loc.getString("text"),
        loc.getStringList("longitude").asScala.toList, loc.getStringList("latitude").asScala.toList, loc.getStringList("capital").asScala.toList)
    }.toList
  }

  def extractTotalWords: Future[Either[String, Seq[Marker]]]

  def extractWords(placeId: Long): Future[Either[String, Marker]]

  def extractWord(id: Long): Future[Either[String, Word]]

  def updateWord(word: Word): Future[Either[String, Int]]

  def deleteWord(id: Long): Future[Either[String, Int]]
}

trait WordsServiceImpl extends WordsService {
  private val log = LoggerFactory.getLogger(this.getClass.getSimpleName)

  protected implicit val system = ActorSystem("system")
  protected implicit val materializer = ActorMaterializer()
  protected implicit val ec = system.dispatcher

  override def extractTotalWords: Future[Either[String, List[Marker]]] = {
    val w = (dictionaryTable join scoreTable on ((d, s) => d.id === s.dictionaryId)).sortBy(_._1.placeId)
      .take(modules.config.getString("data.limit").toInt)
      .map { case (d, s) => (s.id, s.dictionaryId, d.name, d.placeId, d.country_code, d.place, s.count, d.country) }.result
    val wordList = modules.db.run(w)

    def getMarkers = wordList.flatMap { scores =>
      var markerList = new ListBuffer[Marker]()
      scores.zipWithIndex.foldLeft(List[Word]()) {
        case (list, sWithIndex) if (list.isEmpty) =>
          List(Word(sWithIndex._1._1, sWithIndex._1._3.getOrElse(""), sWithIndex._1._4.getOrElse(0L), sWithIndex._1._5.getOrElse(""), sWithIndex._1._6.getOrElse(""), sWithIndex._1._7.getOrElse(0L)))
        case (list, sWithIndex) =>
          sWithIndex._1._4 match {
            case Some(p) if (list.last.placeId != p) =>
              val last = list.last
              val prefLocation = prefLocations.filter(x => (x.id == last.placeId)).head
              val counter = countUp(list)
              markerList += Marker(last.placeId, prefLocation.capital.head.toDouble, prefLocation.capital.last.toDouble, last.place, "", list, counter)
              val words = List(Word(sWithIndex._1._1, sWithIndex._1._3.getOrElse(""), sWithIndex._1._4.getOrElse(0L), sWithIndex._1._5.getOrElse(""), sWithIndex._1._6.getOrElse(""), sWithIndex._1._7.getOrElse(0L)))
              if (scores.last._4 == sWithIndex._1._4 && scores.last._2 == sWithIndex._1._2) {
                val prefLocation = prefLocations.filter(x => (x.id == sWithIndex._1._4.head)).head
                val counter = countUp(words)
                markerList += Marker(sWithIndex._1._4.getOrElse(0L), prefLocation.capital.head.toDouble, prefLocation.capital.last.toDouble, sWithIndex._1._6.getOrElse(""), "", words, counter)
              }
              words
            case Some(l) if (scores.last._4 == sWithIndex._1._4 && scores.last._2 == sWithIndex._1._2) =>
              val prefLocation = prefLocations.filter(x => (x.id == sWithIndex._1._4.head)).head
              val words = list :+ Word(sWithIndex._1._1, sWithIndex._1._3.getOrElse(""), sWithIndex._1._4.getOrElse(0L), sWithIndex._1._5.getOrElse(""), sWithIndex._1._6.getOrElse(""), sWithIndex._1._7.getOrElse(0L))
              val counter = countUp(list :+ Word(sWithIndex._1._1, sWithIndex._1._3.getOrElse(""), sWithIndex._1._4.getOrElse(0L), sWithIndex._1._5.getOrElse(""), sWithIndex._1._6.getOrElse(""), sWithIndex._1._7.getOrElse(0L)))
              markerList += Marker(sWithIndex._1._4.getOrElse(0L), prefLocation.capital.head.toDouble, prefLocation.capital.last.toDouble, sWithIndex._1._6.getOrElse(""), "", words, counter)
              List(Word(sWithIndex._1._1, sWithIndex._1._3.getOrElse(""), sWithIndex._1._4.getOrElse(0L), sWithIndex._1._5.getOrElse(""), sWithIndex._1._6.getOrElse(""), sWithIndex._1._7.getOrElse(0L)))
            case Some(_) => list :+ Word(sWithIndex._1._1, sWithIndex._1._3.getOrElse(""), sWithIndex._1._4.getOrElse(0L), sWithIndex._1._5.getOrElse(""), sWithIndex._1._6.getOrElse(""), sWithIndex._1._7.getOrElse(0L))
            case None => list
          }
      }
      Future.successful(Right(markerList.toList))
    }

    getMarkers
  }

  override def extractWords(placeId: Long): Future[Either[String, Marker]] = {
    val w = (dictionaryTable join scoreTable on ((d, s) => d.id === s.dictionaryId)).filter(_._1.placeId === placeId)
      .take(modules.config.getString("data.limit").toInt).sortBy(_._1.placeId)
      .map { case (d, s) => (s.id, s.dictionaryId, d.name, d.placeId, d.country_code, d.place, s.count, d.country) }.result
    val wordList = modules.db.run(w)

    def getMarkers = wordList.flatMap { scores =>
      val preLocation = prefLocations.filter(_.id == placeId).head
      // words list each by pref
      val words = scores.foldLeft(List[Word]()) {
        (b, score) => b :+ Word(score._1, score._3.getOrElse(""), score._4.getOrElse(0L), score._5.getOrElse(""), preLocation.text, score._7.getOrElse(0L))
      }
      val counter = countUp(words)
      Future.successful(Right(Marker(words.head.placeId, preLocation.capital.head.toDouble, preLocation.capital.last.toDouble, words.head.place, "", words, counter)))
    }

    getMarkers
  }

  private def countUp(words: List[Word]) = {
    words.foldLeft(0L) {
      (counter: Long, w: Word) => counter + w.count
    }
  }

  override def extractWord(id: Long): Future[Either[String, Word]] = {
    val w = (dictionaryTable join scoreTable on ((d, s) => d.id === s.dictionaryId)).filter(_._2.id === id).sortBy(_._1.placeId)
      .map {
        case (d, s) => (s.id, s.dictionaryId, d.name, d.placeId, d.country_code, d.place, s.count, d.country)
      }.result.headOption
    val word = modules.db.run(w)
    word flatMap {
      res =>
        res match {
          case Some(wd) => Future(Right(Word(wd._1, wd._3.getOrElse(""), wd._4.getOrElse(0L), wd._5.getOrElse(""), wd._6.getOrElse(""), wd._7.getOrElse(0L))))
          case None => Future(Left("Failed to get a word"))
        }
    }
  }

  override def updateWord(word: Word): Future[Either[String, Int]] = {
    val dic = (dictionaryTable join scoreTable on ((d, s) => d.id === s.dictionaryId)).filter(_._2.id === word.id)
      .map {
        case (d, s) => (s.id, s.dictionaryId, d.name, d.placeId, d.country_code, d.place, s.count, d.country)
      }.result.headOption
    val res = modules.db.run(dic)
    res flatMap { res =>
      res match {
        case Some(d) =>
          val update: DBIOAction[Int, NoStream, Effect.Write] = dictionaryTable.filter(_.id === d._2)
            .map(_.name).update(Option(word.name))
          val result = dictionaryDal.execute(update)
          result.flatMap {
            case 0 => Future(Left("Failed to update a word"))
            case upd => Future(Right(upd))
          }
        case None => Future(Left("Failed to update a word"))
      }
    }
  }

  override def deleteWord(id: Long): Future[Either[String, Int]] = {
    val result = scoreDal.deleteById(id)
    result.flatMap {
      case 0 => Future(Left("Failed to delete a word"))
      case del => Future(Right(del))
    }
  }

}
