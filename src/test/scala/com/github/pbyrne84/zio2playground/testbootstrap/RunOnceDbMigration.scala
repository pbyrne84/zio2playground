package com.github.pbyrne84.zio2playground.testbootstrap

import com.github.pbyrne84.zio2playground.db.DbMigration
import zio.{Ref, ZIO, ZLayer}

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
          _ <- dbMigration.run
        } yield (true -> true)
      } else {
        for {
          _ <- ZIO.logInfo("already ran db migration")
        } yield true -> true
      }
    }

  }
}
