package com.restapi

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.twitter.models.{Marker, Word}
import spray.json.{DefaultJsonProtocol, PrettyPrinter, RootJsonFormat}

/**
  * Created by SeungEun on 2017/03/01.
  */
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit lazy val prefFormat = jsonFormat7(Marker)
  implicit lazy val scoreFormat = jsonFormat6(Word)
  implicit val printer = PrettyPrinter
  implicit val menuItemFormat: RootJsonFormat[Word] = jsonFormat(Word.apply, "id", "name", "placeId", "country_code", "place", "count")
}

