package com.resident.actor

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorSystem, OneForOneStrategy, PoisonPill, Props, Terminated}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class MessageBatchActor extends Actor {
  private[this] val system = ActorSystem("sqsMessage")
  val firstDelay = 1 millisecond
  val interval = 10 second
  val sttcsInterval = 15 second
  val delInterval = 30 second

  // the setting for exceptions
  // It's allowed to restart within 10 times for 60 sec.
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
    case _: ArithmeticException => Resume
    case _: NullPointerException => Restart
    case _: IllegalArgumentException => Stop
    case _: Exception => Escalate
  }

  override def receive = {
    case MessageBatchActor.Watch(actorName) =>
      val sqsMsgActor = system.actorOf(SQSMessageActor.props, actorName)
      context.watch(sqsMsgActor)

      //SQS messages registers start
      system.scheduler.schedule(firstDelay, interval, sqsMsgActor, "kick")
    case MessageBatchActor.WatchExec(actorName) =>
      val execActor = system.actorOf(MessageQueueActor.props, actorName)
      context.watch(execActor)

      //SQS messages queuing start
      system.scheduler.schedule(firstDelay, sttcsInterval, execActor, "statics")
      //Deleting SQS messages start
      system.scheduler.schedule(firstDelay, delInterval, execActor, "delete")
    case Terminated(actor) =>
      context.system.terminate()
      actor ! PoisonPill
  }
}

object MessageBatchActor {

  def props = Props[MessageBatchActor]

  case class Watch(actorName: String)

  case class WatchExec(actorName: String)

}

