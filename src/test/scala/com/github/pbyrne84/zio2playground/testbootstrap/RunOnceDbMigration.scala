package com.github.pbyrne84.zio2playground.testbootstrap

import com.github.pbyrne84.zio2playground.db.DbMigration
import zio.{Ref, ZIO, ZLayer}

import scala.jdk.CollectionConverters.CollectionHasAsScala

object RunOnceDbMigration {

  private val hasRun = false
  val layer: ZLayer[Any, Nothing, RunOnceDbMigration] = ZLayer {
    for {
      hasRunRef <- Ref.Synchronized.make(hasRun)
    } yield new RunOnceDbMigration(hasRunRef)
  }
}

class RunOnceDbMigration(hasRunRef: Ref.Synchronized[Boolean]) {

  def run(dbMigration: DbMigration): ZIO[Any, Nothing, Boolean] = {
    hasRunRef.modifyZIO { hasRun =>
      if (!hasRun) {
        for {
          _ <- ZIO.logInfo("running db migration")
          result <- dbMigration.run
          _ <- ZIO.logInfo(
            s"""successfully ran migrations ${result.map(_.success)} ${result.map(
                _.migrations.asScala.map(_.filepath).mkString(", ")
              )}"""
          )
        } yield (true -> true)
      } else {
        for {
          _ <- ZIO.logInfo("already ran db migration")
        } yield true -> true
      }
    }

  }
}
