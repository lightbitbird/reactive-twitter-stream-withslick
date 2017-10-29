package com.twitter.entities

import java.sql.Timestamp

import com.persistence.entities.{BaseEntity, BaseTable}
import slick.driver.H2Driver.api._

case class Score(id: Long, dictionaryId: Option[Long], count: Option[Long], sqsId: Option[String],
                 created: Option[Timestamp], updated: Option[Timestamp]) extends BaseEntity

case class SimpleScore(dictionaryId: Option[Long], count: Option[Long], sqsId: Option[String],
                       created: Option[Timestamp], updated: Option[Timestamp])

class Scores(tag: Tag) extends BaseTable[Score](tag, "score") {
  //  def placeId = column[Long]("placeId")
  //
  //  def name = column[Option[String]]("name")
  //
  //  def country_code = column[Option[String]]("country_code")
  //
  //  def country = column[Option[String]]("country")
  //
  //  def place = column[Option[String]]("place")
  //
  def dictionaryId = column[Option[Long]]("dictionaryId")

  def count = column[Option[Long]]("count")

  def sqsId = column[Option[String]]("sqsId")

  def created = column[Option[Timestamp]]("created")

  def updated = column[Option[Timestamp]]("updated")

  def * = (id, dictionaryId, count, sqsId, created, updated) <> (Score.tupled, Score.unapply)
}
