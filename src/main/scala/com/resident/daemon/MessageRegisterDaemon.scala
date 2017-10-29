package com.tweet.daemon

import akka.actor.{ActorRef, ActorSystem, Cancellable}
import com.resident.actor.{MessageBatchActor, SQSMessageActor}
import org.apache.commons.daemon.{Daemon, DaemonContext}

class MessageRegisterDaemon(appName: String) extends Daemon {
  private[this] val system = ActorSystem(appName)

  override def init(context: DaemonContext) {}

  override def start: Unit = {
    val actor = system.actorOf(MessageBatchActor.props, "actor")
    //Watch the SQS message actor
    actor ! MessageBatchActor.Watch("sqsMsgActor")
    actor ! MessageBatchActor.WatchExec("queueActor")
  }

  override def stop {}

  override def destroy(): Unit = ???

  def restart(cancellable: Cancellable, actor: ActorRef): Unit = if (cancellable.isCancelled) start
}

