package com.twitter.daemon

import akka.actor.{ActorRef, ActorSystem, Cancellable}
import com.twitter.actor.TwitterBatchActor
import org.apache.commons.daemon.{Daemon, DaemonContext}

class TwitterStreamingDaemon(appName: String) extends Daemon {
  private[this] val system = ActorSystem(appName)

  override def init(context: DaemonContext): Unit = {}

  override def start(): Unit = {
    val actor = system.actorOf(TwitterBatchActor.props, "actor")

    actor ! TwitterBatchActor.Watch("twitterActor")

  }

  override def destroy(): Unit = {}

  override def stop(): Unit = {}

  def restart(cancellable: Cancellable, actor: ActorRef): Unit = if (cancellable.isCancelled) start
}
