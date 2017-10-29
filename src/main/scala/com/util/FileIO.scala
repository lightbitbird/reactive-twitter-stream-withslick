package com.util

import java.io.PrintWriter
import java.nio.file.{Files, Paths}
import scala.io.Source

/**
  * Created by SeungEun on 2017/02/21.
  */
object FileIO {

  def getSource(path: String) = {
    val file = Paths.get(path)
    if (Files.notExists(file)) Files.createFile(file)

    Source.fromFile(path, "UTF-8")
  }

  def getLines(path: String): (Iterator[String], Source) = {
    val source = getSource(path)
    (source.getLines(), source)
  }

  def readList(path: String): List[String] = {
    val lines = getLines(path)
    try {
      lines._1.toList
    } finally lines._2
  }

  def readAll(path: String): String = {
    val lines = getLines(path)
    try {
      lines._1.foldLeft("")((b, a) => b + "\n" + a)
    } finally lines._2
  }

  def write(path: String, text: String) = {
    val pw = new PrintWriter(path)
    try {
      pw.write(text)
    } finally pw.close()
  }

  def write(path: String, lines: List[String]) = {
    val pw = new PrintWriter(path)
    try {
      pw.write(lines.foldLeft("")((b, a) => {
        if (b.nonEmpty) b + "\n" + a
        else b + a
      }))
    } finally pw.close()
  }

}
