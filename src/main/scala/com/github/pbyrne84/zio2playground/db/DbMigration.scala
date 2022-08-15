package com.github.pbyrne84.zio2playground.db

import com.github.pbyrne84.zio2playground.config.DbConfig
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import zio.{ULayer, ZIO, ZLayer}

import scala.util.Try

object DbMigration {
  val layer: ZLayer[DbConfig, Nothing, DbMigration] = ZLayer {
    for {
      config <- ZIO.service[DbConfig]
    } yield new DbMigration(config)
  }

  def runDbMigration: ZIO[DbMigration, Nothing, Either[Throwable, MigrateResult]] =
    ZIO.serviceWithZIO[DbMigration](_.run)

}

class DbMigration(dbConfig: DbConfig) {
  def run: ZIO[Any, Nothing, Either[Throwable, MigrateResult]] = {
    ZIO.succeed {
      Try {
        val flyway = Flyway.configure
          .dataSource(dbConfig.jdbcUrl, dbConfig.user, dbConfig.password)
          .load

        flyway.migrate()
      }.toEither
    }
  }
}
