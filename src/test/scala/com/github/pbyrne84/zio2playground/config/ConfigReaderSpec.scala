package com.github.pbyrne84.zio2playground.config

import com.github.pbyrne84.zio2playground.BaseSpec
import com.github.pbyrne84.zio2playground.testbootstrap.AllTestBootstrap
import zio.ZIO
import zio.test.TestAspect.sequential
import zio.test._

object ConfigReaderSpec extends BaseSpec {

  override def spec = {
    suite("Config Reader")(
      suite("getDatabaseConfig") {
        test("reads the config successfully") {
          val expected = DbConfig(
            datasourceClassName = "org.h2.jdbcx.JdbcDataSource",
            password = "",
            jdbcUrl =
              "jdbc:h2:file:./testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            user = "sa"
          )

          for {
            _ <- reset
            config <- AllTestBootstrap.getParams
            _ <- ZIO.logInfo(s"current config $config")
            actualConfig <- ZIO
              .service[DbConfig]
              .provide(ConfigReader.getDatabaseConfigLayer)
          } yield {
            assertTrue(actualConfig == expected)
          }
        }
      },
      suite("getRemoteServicesConfigLayer") {
        test("reads the config successfully") {
          for {
            _ <- reset
            config <- AllTestBootstrap.getParams
            _ <- ZIO.logInfo(s"current config $config")
            expected = RemoteServicesConfig(
              s"http://localhost:${config.serverAPort}",
              s"http://localhost:${config.serverBPort}"
            )
            actual <- ZIO
              .service[RemoteServicesConfig]
              .provide(ConfigReader.getRemoteServicesConfigLayer)

          } yield assertTrue(actual == expected)
        }
      }
    )
  } @@ sequential

}
