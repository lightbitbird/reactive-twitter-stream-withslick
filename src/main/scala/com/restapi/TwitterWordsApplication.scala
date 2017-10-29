package com.restapi

import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.Directives
import ch.megard.akka.http.cors.CorsDirectives._
import com.restapi.services.WordsServiceImpl
import com.twitter.models.Word
import slick.driver.H2Driver.api._

object TwitterWordsApplication extends App with WordsServiceImpl with Directives with JsonSupport {
  // marshaller for the case class Word
  implicit val itemFormat = jsonFormat6(Word)

  private val interface = "localhost"
  private val port = 8080
  private val logger = Logging(system, getClass)

  private val routes = cors()({
    pathSingleSlash {
      get {
        val ret = extractTotalWords
        onSuccess(ret) {
          case Right(markers) => complete(ToResponseMarshallable(markers))
          case Left(string) => complete(ToResponseMarshallable(string))
        }
      }
    } ~ pathPrefix("api") {
      (pathEnd | path("words") | path("words-map")) {
        get {
          val ret = extractTotalWords
          onSuccess(ret) {
            case Right(markers) => complete(ToResponseMarshallable(markers))
            case Left(string) => failWith(new Exception(string))
          }
        }
      } ~ path("words-map" / LongNumber) { id =>
        get {
          val ret = extractWords(id)
          onSuccess(ret) {
            case Right(marker) => complete(ToResponseMarshallable(marker))
            case Left(string) => failWith(new Exception(string))
          }
        }
      } ~ path("word" / LongNumber) { id =>
        get {
          val ret = extractWord(id)
          onSuccess(ret) {
            case Right(word) => complete(ToResponseMarshallable(word))
            case Left(string) => failWith(new Exception(string))
          }
        }
      } ~ path("word" / "update") {
        post {
          entity(as[Word]) { word =>
            val ret = updateWord(word)
            onSuccess(ret) {
              case Right(w) => println(w); complete(ToResponseMarshallable(word))
              case Left(s) => failWith(new Exception(s))
            }
          }
        }
      } ~ path("word" / "delete" / LongNumber) { id =>
        post {
          val ret = deleteWord(id)
          onSuccess(ret) {
            case Right(w) => println(w); complete(ToResponseMarshallable(Word(0L, "", 0L, "", "", 0L)))
            case Left(s) => failWith(new Exception(s))
          }
//          complete(ToResponseMarshallable(Word(0L, "", 0L, "", "", 0L)))
        }
      }
    }
  })

  val binding = Http().bindAndHandle(routes, interface, port)
  binding.onFailure {
    case err: Exception =>
      logger.error(err, s"Failed to bind to $interface $port")
  }

}

