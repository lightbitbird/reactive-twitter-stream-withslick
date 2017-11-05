package com.twitter.actor

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorSystem, OneForOneStrategy, PoisonPill, Props, Terminated}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt

class TwitterBatchActor extends Actor {
  private val log = LoggerFactory.getLogger(this.getClass.getSimpleName)

  private[this] val system = ActorSystem("actorSystem")
  private val delay = 1 millisecond
  private val interval = 30 second

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
    case _: ArithmeticException => Resume
    case _: NullPointerException => Restart
    case _: IllegalArgumentException => Stop
    case _: Exception => Escalate
  }

  override def receive: Receive = {
    case TwitterBatchActor.Watch(actorName) =>
      val streamActor = system.actorOf(StreamingActor.props, actorName)
      context.watch(streamActor)
      //Choose a direct prefecture number
      streamActor ! 17

    // It might ourre 420 status error
    // system.scheduler.schedule(delay, interval, streamActor, 18)

    case Terminated(actor) =>
      log.error("Twitter Batch Actor Terminated!")
      context.system.terminate()
      actor ! PoisonPill
  }

}

object TwitterBatchActor {

  val props = Props[TwitterBatchActor]

  case class Watch(actorName: String)

}
