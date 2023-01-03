package com.github.pbyrne84.zio2playground.sharedlayer

import com.typesafe.scalalogging.StrictLogging
import zio.test._
import zio.{Scope, UIO, ZIO, ZLayer}

/** Currently with the intellij plugins not compatible with zio the tests do not run correctly and
  * we cannot guarantee that things will execute only once. Annoying.
  *
  * This is a zio app things as the equivalent
  *
  * sbt "Test/runMain com.github.pbyrne84.zio2playground.sharedlayer.TestA"
  *
  * is also errant.
  *
  * Compare this with
  *
  * sbt "Test/testOnly com.github.pbyrne84.zio2playground.sharedlayer.TestA"
  *
  * The build sbt also need tests not to be parallel or forked. Forked affects the reporting though.
  */
object ExpensiveService {
  def log(message: String): UIO[Unit] = ZIO.logInfo(message)

  val layer: ZLayer[Any, Nothing, ExpensiveService] = {

    ZLayer.scoped {
      val layer = ZIO.acquireRelease {
        ZIO.succeed(ExpensiveService())
      }(a => log("releasing ExpensiveService"))

      layer
    }
  }

  def boo: ZIO[ExpensiveService, Nothing, Unit] = ZIO.service[ExpensiveService].map(_.boo)

}

//This when run from commandline only creates 1, when run in intellij creates 3 how ever many things run ?
//Across different threads. This message is repeated in many forms as wondering why this is being
//logged should raise suspicions.
case class ExpensiveService() extends StrictLogging {
  logger.info(s"created ExpensiveService $getClass ${Thread.currentThread()}")

  def boo: Unit = logger.info("booo")
}

object SharedService {
  val layer: ZLayer[Any, Nothing, SharedService] = {

    def log(message: String): UIO[Unit] = ZIO.log(message)

    ZLayer.scoped {
      val layer = ZIO.acquireRelease {
        ZIO.succeed(SharedService())
      }(a => log("releasing SharedService"))

      layer
    }
  }

  def boo: ZIO[SharedService, Nothing, Unit] = ZIO.service[SharedService].map(_.boo)

}

case class SharedService() extends StrictLogging {
  // see all other comments :)
  logger.info(s"created SharedService $getClass")

  def boo: Unit = println("booo")
}

object SharedLayerBaseSpec {}

abstract class SharedLayerBaseSpec extends ZIOSpec[ExpensiveService] {

  override def bootstrap: ZLayer[Any, Nothing, ExpensiveService] = ExpensiveService.layer
}

object TestA extends SharedLayerBaseSpec {

  override def spec: Spec[ExpensiveService with TestEnvironment with Scope, Any] = suite("aa")(
    test("test 1") {
      for {
        _ <- SharedService.boo
      } yield assertTrue(true)
    },
    test("test 3") {
      for {
        _ <- SharedService.boo
      } yield assertTrue(true)
    }
  ).provideShared(SharedService.layer) // this works correctly creating 1 across the tests
}
