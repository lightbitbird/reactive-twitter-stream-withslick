package com.restapi

import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import com.restapi.routes.WordsHttpRoute
import com.restapi.services.WordsServiceImpl

import scala.util.Failure

object TwitterWordsApplication extends App with WordsServiceImpl with Directives with JsonSupport {
  private val logger = Logging(system, getClass)

  val interface = "localhost"
  val port = 8080

  val binding = Http().bindAndHandle(WordsHttpRoute.route, interface, port)
  binding.onComplete {
    case scala.util.Success(s) => logger.info("Success")
    case Failure(f) => logger.error(f, s"Failed to bind to $interface $port")
  }

}

