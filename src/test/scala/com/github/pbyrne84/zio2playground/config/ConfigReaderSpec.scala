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
            datasourceClassName = "org.postgresql.ds.PGSimpleDataSource",
            databaseName = "testdb",
            password = "password",
            jdbcUrl = "jdbc:postgresql://localhost/testdb?user=postgres",
            user = "postgres"
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
