package com.github.pbyrne84.zio2playground.config

import com.typesafe.config.{Config, ConfigFactory}
import zio.config._
import zio.config.typesafe.TypesafeConfig
import zio.{Layer, Task, ZIO}

object ConfigReader {
  private lazy val configTask: Task[Config] = {
    ZIO.attempt(ConfigFactory.load("application.conf"))
  }

  def getDatabaseConfigLayer: Layer[ReadError[String], DbConfig] =
    TypesafeConfig.fromTypesafeConfig(
      configTask,
      DbConfig.dbConfigDescriptor
    )

  def getRemoteServicesConfigLayer: Layer[ReadError[String], RemoteServicesConfig] =
    TypesafeConfig.fromTypesafeConfig(
      configTask,
      RemoteServicesConfig.remoteServicesDescriptor
    )
}
