package com.twitter.models

/**
  * Created by SeungEun on 2017/02/05.
  */
case class Marker(placeId: Long, lat: Double, lng: Double, place: String, label: String, words: Seq[Word], total: Long)

case class Word(id: Long, name: String, placeId: Long, country_code: String, place: String, count: Long)

