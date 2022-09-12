package com.github.pbyrne84.zio2playground.testbootstrap

import com.github.pbyrne84.zio2playground.config.ConfigReader
import com.github.pbyrne84.zio2playground.db.DbMigration
import zio.{Ref, ZIO, ZLayer}

object AllTestBootstrap {

  val layer: ZLayer[InitialisedParams with RunOnceDbMigration, Nothing, AllTestBootstrap] = {
    ZLayer {
      for {
        dbMigration <- ZIO.service[RunOnceDbMigration]
        environmentParamSetup <- ZIO.service[InitialisedParams]
        counter <- Ref.Synchronized.make(0)
      } yield new AllTestBootstrap(dbMigration, counter, environmentParamSetup)
    }
  }

  def reset: ZIO[AllTestBootstrap, Throwable, Unit] =
    ZIO.serviceWithZIO[AllTestBootstrap](_.reset)

  def getParams: ZIO[AllTestBootstrap, Throwable, InitialisedParams] =
    ZIO.serviceWithZIO[AllTestBootstrap](_.getParams)
}

class AllTestBootstrap(
    runOnceDbMigration: RunOnceDbMigration,
    counter: Ref.Synchronized[Int],
    initialisedParams: InitialisedParams
) {

  def reset: ZIO[Any, Throwable, Unit] = {
    for {

      // we need to make sure we have done the config param stuff before reading any config layer
      // or there is a big freak out. DbMigration was in the constructor but that caused the layer
      // to be required to early. application.conf being a global concept causes these fights.
      // It can be worth having a separate conf for the application if you have logging libraries
      // that rely on the default else logging breaks when the params are not set leading
      // to potentially obfuscated messages.
      dbMigration <- ZIO
        .service[DbMigration]
        .provide(DbMigration.layer, ConfigReader.getDatabaseConfigLayer)
      newCount <- counter.modify(count => (count + 1, count + 1))
      _ = ZIO.logInfo(s"Called x times ${newCount}")
      _ <- runOnceDbMigration.run(dbMigration)
    } yield ()
  }

  def getParams: ZIO[Any, Throwable, InitialisedParams] =
    ZIO.succeed(initialisedParams)
}
