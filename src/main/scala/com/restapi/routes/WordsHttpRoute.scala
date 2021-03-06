package com.restapi.routes

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.Directives
import ch.megard.akka.http.cors.CorsDirectives._
import com.restapi.JsonSupport
import com.restapi.services.WordsServiceImpl
import com.twitter.models.Word

class WordsHttpRoute() extends WordsServiceImpl with Directives with JsonSupport {

  // marshaller for the case class Word
  implicit val itemFormat = jsonFormat6(Word)

  val route = cors()({
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
              case Right(w) => complete(ToResponseMarshallable(word))
              case Left(s) => failWith(new Exception(s))
            }
          }
        }
      } ~ path("word" / "delete" / LongNumber) { id =>
        post {
          val ret = deleteWord(id)
          onSuccess(ret) {
            case Right(w) => complete(ToResponseMarshallable(Word(0L, "", 0L, "", "", 0L)))
            case Left(s) => failWith(new Exception(s))
          }
        }
      }
    }
  })

}

