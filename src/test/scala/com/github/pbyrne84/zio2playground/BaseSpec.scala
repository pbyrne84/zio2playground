package com.github.pbyrne84.zio2playground

import com.github.pbyrne84.zio2playground.testbootstrap.wiremock.ServerAWireMock
import com.github.pbyrne84.zio2playground.testbootstrap.{
  AllTestBootstrap,
  EnvironmentParamSetup,
  InitialisedParams,
  RunOnceDbMigration
}
import zio.test.ZIOSpec
import zio.{ZIO, ZLayer}

object BaseSpec {
  private val layer = {
    ZLayer.make[
      AllTestBootstrap with InitialisedParams with EnvironmentParamSetup
    ](
      AllTestBootstrap.layer,
      RunOnceDbMigration.layer,
      InitialisedParams.layer,
      EnvironmentParamSetup.layer
    )
  }

  val layerWithWireMock: ZLayer[
    Any,
    Nothing,
    AllTestBootstrap with ServerAWireMock with InitialisedParams with EnvironmentParamSetup
  ] = {
    ZLayer.make[
      AllTestBootstrap with ServerAWireMock with InitialisedParams with EnvironmentParamSetup
    ](
      BaseSpec.layer,
      ServerAWireMock.layer
    )
  }

  type Shared = AllTestBootstrap
    with ServerAWireMock
    with InitialisedParams
    with EnvironmentParamSetup
}

abstract class BaseSpec extends ZIOSpec[BaseSpec.Shared] {

  println("dsssss " + getClass)

  val bootstrap = BaseSpec.layerWithWireMock

  def reset: ZIO[BaseSpec.Shared, Throwable, Unit] = {
    for {
      _ <- AllTestBootstrap.reset
      _ <- ServerAWireMock.reset
    } yield ()
  }

  def getConfig: ZIO[EnvironmentParamSetup, Nothing, InitialisedParams] =
    InitialisedParams.getParams

}
