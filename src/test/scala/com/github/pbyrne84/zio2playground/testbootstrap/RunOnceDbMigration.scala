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
        println("running db migration")
        for {
          _ <- dbMigration.run
        } yield (true -> true)
      } else {
        println("already ran db migration")
        ZIO.succeed(true -> true)
      }
    }

  }
}
