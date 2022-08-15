package com.github.pbyrne84.zio2playground.config

object DbConfig {

  import zio.config._
  import ConfigDescriptor._

  private[config] val dbConfigDescriptor: _root_.zio.config.ConfigDescriptor[DbConfig] =
    nested("testPostgresDB")(
      string("dataSourceClassName") zip
        nested("dataSource")(
          string("databaseName") zip
            string("password") zip
            string("user") zip
            string("url")
        )
    ).map { case (datasourceClassName, (databaseName, password, user, jdbcUrl)) =>
      DbConfig(
        datasourceClassName = datasourceClassName,
        databaseName = databaseName,
        password = password,
        jdbcUrl = jdbcUrl,
        user = user
      )
    }
}

case class DbConfig(
    datasourceClassName: String,
    databaseName: String,
    user: String,
    password: String,
    jdbcUrl: String
)
