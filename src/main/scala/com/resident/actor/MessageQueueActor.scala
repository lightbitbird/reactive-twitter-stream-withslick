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
import org.slf4j.LoggerFactory
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import scala.collection.JavaConverters._
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
  private val log = LoggerFactory.getLogger(this.getClass.getSimpleName)

  private val modules = new ConfigurationModuleImpl with ActorModuleImpl with PersistenceModuleImpl
  private val dictionaryDal = modules.dictionaryDal
  private val scoreDal = modules.scoreDal
  private implicit val system = ActorSystem("QueueActor")
  private implicit val materializer = ActorMaterializer()
  private implicit val ec = system.dispatcher
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
  private def availableDelFlow = Flow[Future[Seq[Message]]].mapAsync(2)(ftr =>
    ftr.flatMap(msgs => Future.successful(msgs.foldLeft(Future(Seq.empty[Message]))((b, a) => {
      getMessages(a, b)
    })))
  )

  private def filterDelFlow = Flow[Message].filter(msg => {
    val list = FileIO.readList("src/main/scala/com/sqs/store/messageIds.txt")
    list.nonEmpty && list.contains(msg.getMessageId)
  })

  private def flowFinalize = Flow[Future[Seq[Message]]].mapAsync(2)(ftr =>
    ftr.flatMap(msgs => Future.successful(msgs.map(m => {
      val result = SQSModule.deleteMessage(m)
      result
    })))
  )

  private def innerSink = Sink.foreach[Message](msg => SQSModule.deleteMessage(msg))

  private def deleteMsgGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val source = {
      //message source
      val messages: Seq[Message] = SQSModule.getMessages.asScala
      Source(messages.toList)
    }

    val filter = b.add(filterDelFlow)
    //    val flow = b.add(flowFinalize)
    val sink = b.add(innerSink)

    source ~> filter ~> sink

    ClosedShape
  })

  private def getMessage(messageId: String): Future[Boolean] = {
    val scoreTable = TableQuery[Scores]
    val existSqsIds: Future[Seq[Score]] = scoreDal.findByFilter(scoreTable.filter(_.sqsId === messageId))
    val exists: Future[Boolean] = for {
      res <- existSqsIds
    } yield (res.size > 0)
    exists
  }

  private def getMessages(message: Message, messages: Future[Seq[Message]]): Future[Seq[Message]] = {
    val scoreTable = TableQuery[Scores]
    val existSqsIds: Future[Seq[Score]] = scoreDal.findByFilter(scoreTable.filter(_.sqsId === message.getMessageId))
    val list: Future[Seq[Message]] = for {
      msgs <- messages //Future -> Seq[Message]
      res <- existSqsIds if (res.size > 0)
    } yield msgs :+ message
    list
  }

  //calculate statistics by each word
  private def calcGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val source = {
      val messages: Seq[Message] = SQSModule.getMessages.asScala
      Source(messages.toList)
    }

    val avail = b.add(availableFlow)
    val flow = b.add(scoring)

    source ~> avail ~> flow ~> Sink.ignore

    ClosedShape
  })

  private def availableFlow = Flow[Message].filter(msg => {
    messageList.isEmpty || !messageList.contains(msg.getMessageId)
  })

  private val dictionaryTable = TableQuery[Dictionaries]
  private val scoreTable = TableQuery[Scores]

  private def scoring = Flow[Message].map { msg =>
    val term: List[String] = msg.getBody.split(",").toList

    log.info("term.size = " + term.size)
    if (term.size > 1) {
      val start = term.head.toLong
      val end = term.last.toLong
      log.info("start = " + start + "  ------- end = " + end)

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

  private def addScore(msg: Message, word: Dictionary, end: Long, size: Long) = {
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
          log.info("Fail to get a dictionary")
        }
      case Failure(fail) => log.error(fail.getMessage)
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
              case Failure(f) => log.error(f.getMessage)
            }
          } else {
            val res: Future[Long] = insert(dictionary)
            res.onComplete {
              case Success(s) => restoreMessageIds(end, dictionaryId, msg)
              case Failure(f) => log.error(f.getMessage)
            }
          }
        }
        case Failure(f) => log.error("Fail to get a score")
      }

      def update(score: Seq[Score], dictionary: Dictionary) = {
        log.info("UPDATE : " + score.toString)
        val count = score.head.count match {
          case Some(c) => c + 1
          case None => 1
        }
        scoreDal.update(Score(score.head.id, score.head.dictionaryId, Option(count), score.head.sqsId,
          score.head.created, Option(new Timestamp(new Date().getTime()))))
      }

      def insert(dictionary: Dictionary) = {
        log.info("INSERT : " + dictionary.name)
        val timestamp = new Timestamp(new Date().getTime())
        scoreDal.insert(Score(0L, Option(dictionary.id), Option(1), dictionary.sqsId, Option(timestamp), Option(timestamp)))
      }
    }
  }

  private def restoreMessageIds(end: Long, dictionaryId: Long, message: Message): Unit = {
    if (end == dictionaryId) {
      messageList += message.getMessageId
      FileIO.write("src/main/scala/com/sqs/store/messageIds.txt", messageList.toList)
    }
  }

}

