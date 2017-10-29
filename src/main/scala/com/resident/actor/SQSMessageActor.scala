package com.resident.actor

import java.sql.Timestamp
import java.util.Date

import akka.actor.{Actor, ActorSystem, Props, Terminated}
import akka.stream.ActorMaterializer
import com.amazonaws.services.sqs.model.SendMessageResult
import com.modules.{ActorModuleImpl, ConfigurationModuleImpl, PersistenceModuleImpl}
import com.sqs.SQSModule
import com.twitter.entities.{Dictionaries, Dictionary}
import slick.driver.PostgresDriver.api._
import slick.lifted.TableQuery

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success}

class SQSMessageActor extends Actor {

  import SQSMessageActor._

  override def receive = {
    case "kick" => sendMessage()
    case Terminated(self) => context.stop(self)
  }
}

object SQSMessageActor {
  val dictionaryDal = (new ConfigurationModuleImpl with ActorModuleImpl with PersistenceModuleImpl).dictionaryDal

  implicit val system = ActorSystem("SQSActor")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  def props = Props[SQSMessageActor]

  def sendMessage(): Unit = {
    try {
      val table = TableQuery[Dictionaries]
      val result: Future[Seq[Dictionary]] = dictionaryDal.findByFilter(table.filter(_.sqsId === "").sortBy(_.id))
      val dictionaries = ListBuffer[Dictionary]()
      result onComplete {
        case Success(r) => {
          if (r.nonEmpty) {
            val headId = r.head.id
            val tailId = r.last.id
            val msgResult: SendMessageResult = SQSModule.sendMessage(headId + "," + tailId)
            println("SEND Message: messageID = " + msgResult.getMessageId)
            // add SQS messageId to the each data
            r.foreach { x =>
              dictionaries += Dictionary(x.id, x.name, x.text, x.lang, x.country_code, x.country, x.placeId, x.place, Option(msgResult.getMessageId),
                x.created, Option(new Timestamp(new Date().getTime())))
            }
            println(dictionaries.toString())

            dictionaryDal.update(dictionaries)
          }
        }
        case Failure(f) => println(f.getMessage)
      }
    } catch {
      case e: Exception => println("Error occured: " + e.getMessage)
    }
  }
}


