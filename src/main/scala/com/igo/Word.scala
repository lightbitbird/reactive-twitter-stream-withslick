package com.igo

import akka.http.scaladsl.model.Uri.Query.Cons

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

case class Word(word: String, wordType: String, subType: String)

object Word {
  def apply(word: String, attribute: String): Word = {
    val element = attribute.split(",")
    Word(word, element(0), element(1))
  }

  private def compareType(l: Word, r: Word, types: String): Boolean = l.wordType == r.wordType && l.wordType == types

  private def compareType(l: Word, r: Word, lType: String, rType: String): Boolean = l.wordType == lType && r.wordType == rType

  def combine(tokens: Iterable[Word]): List[Word] = tokens.foldLeft(List[Word]()) {
    case (lists, token) =>
      if (lists.size == 0) lists :+ token
      else {
        (lists.last, token) match {
          case (l, r) if compareType(l, r, "接頭詞", "名詞") => lists.dropRight(1) :+ Word(l.word + r.word, "名詞", "固有名詞")
          case (l, r) if compareType(l, r, "名詞") => lists.dropRight(1) :+ Word(l.word + r.word, "名詞", "固有名詞")
          case (l, r) if compareType(l, r, "名詞", "動詞") => lists.dropRight(1) :+ Word(l.word + r.word, "動詞", "自立") :+ r
          case (l, r) if compareType(l, r, "動詞", "動詞") => lists.dropRight(1) :+ Word(l.word + r.word, "動詞", "自立")
          case (l, r) if compareType(l, r, "動詞", "助動詞") => lists.dropRight(1) :+ Word(l.word + r.word, "動詞", "自立")
          case o => lists :+ token
        }
      }
  }

  def splitBySymbol(tokens: List[Word]): List[Option[Word]] = {
    val split = List("。", "「", "」", ",", "、", ":", "：")
    val result = tokens.foldLeft(List[Option[Word]]()) {
      (list, token) =>
        if (!(token.wordType == "記号" && split.contains(token.word))) list :+ Option(token)
        else list
    }
    result
  }
}