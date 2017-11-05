package com.persistence.dal

import com.modules.{DbModule, Profile}
import com.persistence.entities.{BaseEntity, BaseTable}
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import slick.lifted.{CanBeQueryCondition, TableQuery}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by SeungEun on 2016/11/05.
  */
trait BaseDal[T, A] {

  def execute(result: DBIOAction[Int, NoStream, Effect.Write]): Future[Int]

  def select(query: Query[T, A, Seq]): Future[Seq[A]]

  def insert(row: A): Future[Long]

  def insert(rows: Seq[A]): Future[Seq[Long]]

  def update(row: A): Future[Int]

  def update(rows: Seq[A]): Future[Unit]

  def findAll(sortBy: Query[T, A, Seq]): Future[Seq[A]]

  def findById(id: Long): Future[Option[A]]

  def findByFilter[T <: Rep[_], Seq[_]](filter: Query[T, A, Seq]): Future[Seq[A]]

  def findByFilter[C: CanBeQueryCondition](f: (T) => C): Future[Seq[A]]

  def deleteById(id: Long): Future[Int]

  def deleteById(ids: Seq[Long]): Future[Int]

  def deleteByFilter[C: CanBeQueryCondition](f: (T) => C): Future[Int]

  def createTable(): Future[Unit]
}

class BaseDalImpl[T <: BaseTable[A], A <: BaseEntity](tableQ: TableQuery[T])(implicit val db: JdbcProfile#Backend#Database, implicit val profile: JdbcProfile)
  extends BaseDal[T, A] with Profile with DbModule {

  import profile.api._

  override def execute(result: DBIOAction[Int, NoStream, Effect.Write]): Future[Int] = {
    db.run(result)
  }

  override def select(query: Query[T, A, Seq]): Future[Seq[A]] = {
    db.run(query.result)
  }

  override def insert(row: A): Future[Long] = {
    insert(Seq(row)).map(_.head)
  }

  override def insert(rows: Seq[A]): Future[Seq[Long]] = {
    db.run(tableQ returning (tableQ).map(_.id) ++= rows.filter(_.isValid))
  }

  override def update(row: A): Future[Int] = {
    if (row.isValid)
      db.run(tableQ.filter(_.id === row.id).update(row))
    else
      Future(0)
  }

  override def update(rows: Seq[A]): Future[Unit] = {
    db.run(DBIO.seq((rows.filter(_.isValid).map(r => tableQ.filter(_.id === r.id).update(r))): _*))
  }

  override def findAll(sortBy: Query[T, A, Seq]): Future[Seq[A]] = {
    db.run(sortBy.result)
  }

  override def findById(id: Long): Future[Option[A]] = {
    db.run(tableQ.filter(_.id === id).result.headOption)
  }

  override def findByFilter[C: CanBeQueryCondition](f: (T) => C): Future[Seq[A]] = {
    db.run(tableQ.withFilter(f).result)
  }

  override def findByFilter[T <: Rep[_], Seq[_]](filter: Query[T, A, Seq]): Future[Seq[A]] = {
    db.run(filter.result)
  }

  override def deleteById(id: Long): Future[Int] = {
    deleteById(Seq(id))
  }

  override def deleteById(ids: Seq[Long]): Future[Int] = {
    db.run(tableQ.filter(_.id.inSet(ids)).delete)
  }

  override def deleteByFilter[C: CanBeQueryCondition](f: (T) => C): Future[Int] = {
    db.run(tableQ.withFilter(f).delete)
  }

  override def createTable(): Future[Unit] = {
    db.run(DBIO.seq(tableQ.schema.create))
  }
}

