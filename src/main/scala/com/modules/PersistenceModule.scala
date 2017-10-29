package com.modules

import com.persistence.dal.{BaseDal, BaseDalImpl}
import com.twitter.entities.{Dictionaries, Dictionary, Score, Scores}
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.lifted.TableQuery

/**
  * Created by SeungEun on 2016/11/05.
  */
trait Profile {
  val profile: JdbcProfile
}

trait DbModule extends Profile {
  val db: JdbcProfile#Backend#Database
}

trait PersistenceModule {
  val dictionaryDal: BaseDal[Dictionaries, Dictionary]
  val scoreDal: BaseDal[Scores, Score]
}

trait PersistenceModuleImpl extends PersistenceModule with DbModule {
  this: Configuration =>

  // use an alternative database configuration ex:
  private val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig("pgdb")
  //    private val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig("h2db")
  //    private val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig("h2test")

  override implicit val profile: JdbcProfile = dbConfig.driver
  override implicit val db: JdbcProfile#Backend#Database = dbConfig.db

  override val dictionaryDal = new BaseDalImpl[Dictionaries, Dictionary](TableQuery[Dictionaries]) {}
  override val scoreDal = new BaseDalImpl[Scores, Score](TableQuery[Scores]) {}
}
