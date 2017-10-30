package com.resident.actor

import java.sql.Timestamp
import java.util.Date

import akka.actor.{Actor, ActorSystem, Props, Terminated}
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, ClosedShape}
import com.amazonaws.services.sqs.model.Message
import com.modules.{ActorModuleImpl, ConfigurationModuleImpl, PersistenceModuleImpl}
import com.sqs.SQSModule
import com.twitter.entities.{Dictionaries, Dictionary, Score, Scores}
import com.util.FileIO
import slick.driver.PostgresDriver.api._
import slick.lifted.TableQuery

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success}

class MessageQueueActor extends Actor {

  import MessageQueueActor._

  override def receive = {
    case "delete" => deleteMessage

    case "statics" => calcStatistics()
    case Terminated(self) => context.stop(self)
  }
}

object MessageQueueActor {
  val modules = new ConfigurationModuleImpl with ActorModuleImpl with PersistenceModuleImpl
  val dictionaryDal = modules.dictionaryDal
  val scoreDal = modules.scoreDal
  implicit val system = ActorSystem("QueueActor")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  private val messageList: ListBuffer[String] = ListBuffer()

  modules.scoreDal.createTable()
  messageList ++= FileIO.readList("src/main/scala/com/sqs/store/messageIds.txt")

  def props = Props[MessageQueueActor]

  def deleteMessage = {
    deleteMsgGraph.run()
  }

  def calcStatistics(): Unit = {
    calcGraph.run()
  }

  //SQSが既にSCOREに反映されているかフィルタリング
  def availableDelFlow = Flow[Future[Seq[Message]]].mapAsync(2)(ftr =>
    ftr.flatMap(msgs => Future.successful(msgs.foldLeft(Future(Seq.empty[Message]))((b, a) => {
      getMessages(a, b)
    })))
  )

  def filterDelFlow = Flow[Message].filter(msg => {
    val list = FileIO.readList("src/main/scala/com/sqs/store/messageIds.txt")
    list.nonEmpty && list.contains(msg.getMessageId)
  })

  def flowFinalize = Flow[Future[Seq[Message]]].mapAsync(2)(ftr =>
    ftr.flatMap(msgs => Future.successful(msgs.map(m => {
      val result = SQSModule.deleteMessage(m)
      result
    })))
  )

  def innerSink = Sink.foreach[Message](msg => SQSModule.deleteMessage(msg))

  def deleteMsgGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val source = {
      val javaList = SQSModule.getMessages
      //message source
      val messages: Seq[Message] = javaList
      Source(messages.toList)
    }

    val filter = b.add(filterDelFlow)
    //    val flow = b.add(flowFinalize)
    val sink = b.add(innerSink)

    source ~> filter ~> sink

    ClosedShape
  })

  def getMessage(messageId: String): Future[Boolean] = {
    val scoreTable = TableQuery[Scores]
    val existSqsIds: Future[Seq[Score]] = scoreDal.findByFilter(scoreTable.filter(_.sqsId === messageId))
    val exists: Future[Boolean] = for {
      res <- existSqsIds
    } yield (res.size > 0)
    exists
  }

  def getMessages(message: Message, messages: Future[Seq[Message]]): Future[Seq[Message]] = {
    val scoreTable = TableQuery[Scores]
    val existSqsIds: Future[Seq[Score]] = scoreDal.findByFilter(scoreTable.filter(_.sqsId === message.getMessageId))
    println("getMessages : messageId === " + message.getMessageId)
    val list: Future[Seq[Message]] = for {
      msgs <- messages //Future -> Seq[Message]
      res <- existSqsIds if (res.size > 0)
    } yield msgs :+ message
    list
  }

  //calculate statistics by each word
  def calcGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val source = {
      val javaList = SQSModule.getMessages
      val messages: Seq[Message] = javaList
      Source(messages.toList)
    }

    val avail = b.add(availableFlow)
    val flow = b.add(scoring)

    source ~> avail ~> flow ~> Sink.ignore

    ClosedShape
  })

  def availableFlow = Flow[Message].filter(msg => {
    println(messageList.toString())
    messageList.isEmpty || !messageList.contains(msg.getMessageId)
  })

  val dictionaryTable = TableQuery[Dictionaries]
  val scoreTable = TableQuery[Scores]

  def scoring = Flow[Message].map { msg =>
    val term: List[String] = msg.getBody.split(",").toList

    println("term.size = " + term.size)
    if (term.size > 1) {
      val start = term.head.toLong
      val end = term.last.toLong
      println("start = " + start + "  ------- end = " + end)

      //SQSから対象のIDに入るレコードを取得
      val q = (for {
        d <- dictionaryTable if d.id >= start && d.id <= end && d.sqsId === msg.getMessageId
      } yield d).sortBy(_.id)
      val result: Future[Seq[Dictionary]] = dictionaryDal.select(q)
      result.onSuccess { case words =>
        words.foreach { word =>
          addScore(msg, word, end, words.size)
        }
      }
    }
  }

  def addScore(msg: Message, word: Dictionary, end: Long, size: Long) = {
    val dic = dictionaryTable.filter(d => (d.name === word.name) && d.placeId === word.placeId
      && d.country_code === word.country_code).sortBy(_.id)
    val res = dictionaryDal.select(dic)

    res.onComplete {
      case Success(d) =>
        // increment existing score by 1
        if (d.nonEmpty) {
          // increment a score by id
          __addScore(d, word.id, msg, end)
        } else {
          println("Fail to get a dictionary")
        }
      case Failure(fail) => println(fail.getMessage)
    }
  }

  def __addScore(dictionaries: Seq[Dictionary], dictionaryId: Long, msg: Message, end: Long) = {
    val dictionary = dictionaries(0)
    val q = for {
      s <- scoreTable if (s.dictionaryId === dictionary.id)
    } yield s
    val score: Future[Seq[Score]] = scoreDal.select(q)
    score.onComplete {
      case Success(s) => {
        if (s.nonEmpty) {
          val res = update(s, dictionary)
          res.onComplete {
            case Success(s) => restoreMessageIds(end, dictionaryId, msg)
            case Failure(f) => println(f.getMessage)
          }
        } else {
          val res: Future[Long] = insert(dictionary)
          res.onComplete {
            case Success(s) => restoreMessageIds(end, dictionaryId, msg)
            case Failure(f) => println(f.getMessage)
          }
        }
      }
      case Failure(f) => println("Fail to get a score")
    }

    def update(score: Seq[Score], dictionary: Dictionary) = {
      println("UPDATE : " + score.toString)
      val count = score.head.count match {
        case Some(c) => c + 1
        case None => 1
      }
      scoreDal.update(Score(score.head.id, score.head.dictionaryId, Option(count), score.head.sqsId,
        score.head.created, Option(new Timestamp(new Date().getTime()))))
    }

    def insert(dictionary: Dictionary) = {
      println("INSERT : " + dictionary.name)
      val timestamp = new Timestamp(new Date().getTime())
      scoreDal.insert(Score(0L, Option(dictionary.id), Option(1), dictionary.sqsId, Option(timestamp), Option(timestamp)))
    }
  }

  def restoreMessageIds(end: Long, dictionaryId: Long, message: Message): Unit = {
    if (end == dictionaryId) {
      messageList += message.getMessageId
      FileIO.write("src/main/scala/com/sqs/store/messageIds.txt", messageList.toList)
    }
  }

}
