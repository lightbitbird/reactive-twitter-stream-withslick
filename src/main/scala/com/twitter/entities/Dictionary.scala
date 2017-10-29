package com.twitter.entities

import java.sql.Timestamp

import com.persistence.entities.{BaseEntity, BaseTable}
import slick.driver.H2Driver.api._

/**
  * Created by SeungEun on 2016/11/05.
  */
case class Dictionary(id: Long, name: Option[String], text: Option[String], lang: Option[String], country_code: Option[String], country: Option[String],
                      placeId: Option[Long], place: Option[String], sqsId: Option[String], created: Option[Timestamp], updated: Option[Timestamp]) extends BaseEntity

case class SimpleDictionary(name: Option[String], text: Option[String], lang: Option[String], country_code: Option[String], country: Option[String],
                            placeId: Option[Long], place: Option[String], sqsId: Option[String], created: Option[Timestamp], updated: Option[Timestamp])

class Dictionaries(tag: Tag) extends BaseTable[Dictionary](tag, "dictionary") {
  def name = column[Option[String]]("name")
  
  def text = column[Option[String]]("text")
  
  def lang = column[Option[String]]("lang")
  
  def country_code = column[Option[String]]("country_code")
  
  def country = column[Option[String]]("country")
  
  def placeId = column[Option[Long]]("placeId")
  
  def place = column[Option[String]]("place")
  
  def sqsId = column[Option[String]]("sqsId")
  
  def created = column[Option[Timestamp]]("created")
  
  //    def created = column[Option[Timestamp]]("created", SqlType("timestamp not null default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP"))
  
  def updated = column[Option[Timestamp]]("updated")
  
  //    def updated = column[Option[Timestamp]]("updated", SqlType("timestamp not null default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP"))
  
  def * = (id, name, text, lang, country_code, country, placeId, place, sqsId, created, updated) <> (Dictionary.tupled, Dictionary.unapply)
}

//object Dictionaries extends TableQuery(new Dictionaries(_))
