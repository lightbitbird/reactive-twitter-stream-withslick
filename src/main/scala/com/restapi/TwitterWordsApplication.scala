package com.restapi

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.restapi.routes.WordsHttpRoute

import scala.util.Failure

object TwitterWordsApplication extends App {
  implicit val system = ActorSystem("system")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  private val logger = Logging(system, getClass)

  val interface = "localhost"
  val port = 8080

  def startApplication = {
    val binding = Http().bindAndHandle(new WordsHttpRoute().route, interface, port)
    binding.onComplete {
      case scala.util.Success(s) => logger.info("Success")
      case Failure(f) => logger.error(f, s"Failed to bind to $interface $port")
    }
  }

  startApplication
}

