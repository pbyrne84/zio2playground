package com.github.pbyrne84.zio2playground.db

import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill
import org.h2.jdbcx.JdbcDataSource
import zio.ZLayer

import javax.sql.DataSource

trait QuillDbConfig {
  val quillLayer: ZLayer[DataSource, Nothing, Quill.Postgres[SnakeCase.type]] =
    Quill.Postgres
      .fromNamingStrategy(SnakeCase)
      .asInstanceOf[ZLayer[DataSource, Nothing, Quill.Postgres[SnakeCase.type]]]

  val dsLayer: ZLayer[Any, Throwable, DataSource] = Quill.DataSource.fromPrefix("testDB")
}
