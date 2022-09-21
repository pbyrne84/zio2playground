package com.github.pbyrne84.zio2playground.config

object DbConfig {

  import zio.config._
  import ConfigDescriptor._

  private[config] val dbConfigDescriptor: _root_.zio.config.ConfigDescriptor[DbConfig] =
    (nested("testDB")(
      string("dataSourceClassName") zip
        nested("dataSource")(
          string("password") zip
            string("user") zip
            string("url")
        )
    )).map { case (datasourceClassName, (password, user, jdbcUrl)) =>
      DbConfig(
        datasourceClassName = datasourceClassName,
        password = password,
        jdbcUrl = jdbcUrl,
        user = user
      )
    }
}

case class DbConfig(
    datasourceClassName: String,
    user: String,
    password: String,
    jdbcUrl: String
)
