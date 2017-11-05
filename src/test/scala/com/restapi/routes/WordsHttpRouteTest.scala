package com.restapi.routes

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}

class WordsHttpRouteTest extends WordSpec with Matchers with ScalatestRouteTest {

  val interface = "localhost"
  val port = 8080

  "CoreRoutes" should {
    "return the status for GET on the /twitter words endpoint" in {
      Get("/api/words") ~> WordsHttpRoute.route ~> check {
        status.intValue() shouldBe 200
        //responseAs[String] shouldBe """{"status": "Ok"}"""
      }
    }

    "return the build info for GET on the /twitter words google map endpoint" in {
      Get("/api/words-map") ~> WordsHttpRoute.route ~> check {
        status.intValue() shouldBe 200
      }
    }
  }
}
