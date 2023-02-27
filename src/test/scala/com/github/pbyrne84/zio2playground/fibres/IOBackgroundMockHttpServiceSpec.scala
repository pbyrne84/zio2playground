package com.github.pbyrne84.zio2playground.fibres

import cats.effect.{IO, Sync}
import cats.effect.testing.scalatest.AsyncIOSpec
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers
import zio.{FiberRefs, Runtime, RuntimeFlags, ZEnvironment, ZIO}

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

// Experimenting to see if I can create pluggable backends for
// https://github.com/pbyrne84/scalahttpmock
// Hard coding reliance on either IO or ZIO can leave projects
// with dependency conflicts on their major dependencies.
//
//
class IOBackgroundMockHttpServiceSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  // Intellij for the win again finding the magic import
  import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

  "aa" - {
    "aaa" in {
      val ioOperation = new IOCustomOperation()

      for {
        backoundFork <- ioOperation.run
        _ <- ioOperation.createStartupWaitingWebService
        a = 1
      } yield (a shouldBe 1)
    }
  }

//   def spec: Spec[Shared with TestEnvironment with Scope, Any] = {
//    suite(getClass.getSimpleName)(
//      suite("")(
//        test("") { // all the basicRequest/asStringAlways stuff etc.
//          val operation = new IOCustomOperation()
//          for {
//            backoundFork <- operation.run
//            _ <- operation.createStartupWaitingWebService
//            //  _ = Thread.sleep(1000)
//            meow <- ZIO
//              .succeed(println("should shut down"))
//            response <- ZioTestHttpClient.call("http://localhost:8080/text")
//            _ = println(response)
//            _ = println("boop")
//            _ <- ZIO.attempt(Thread.sleep(1000))
//            _ <- ZIO.succeed(println("ssss"))
//            // _ <- backoundFork.interruptFork
//          } yield assertTrue(true)
//        }
//      )
//    )
//  }
}

object IOCustomOperation {}

//Just create something that will run in the background that cannot inhibit test
class IOCustomOperation {
  import cats.effect._
  import cats.syntax.all._
  def run = {
    import cats.instances.list._
    import cats.syntax.foldable._

    def doSomething(counter: Int) = {
      IO
        .blocking { // blocking as there is a Thread.sleep within.
          Thread.sleep(100)
          println(counter)
          counter
        }
    }

    val call = for {
      counters <- IO((0 to 3000).toList)
      _ <- counters.traverse_ { doSomething }
    } yield ()

    call.start
  }

  def createStartupWaitingWebService: IO[Unit] = {

    for {
      _ <- createBackgroundWebService
//      a <- (for {
//        _ <- ZioTestHttpClient
//          .call("http://localhost:8080/text")
//        _ = Thread.sleep(
//          100
//        ) // I actually want a real clock for scheduling etc but in a test there is a test clock
    } yield ()
  }

  private def createBackgroundWebService = {
    // There are multiple versions of Ok etc so keeping imports here makes things
    // easier for my brain
    import cats.effect._, org.http4s._, org.http4s.dsl.io._
    val helloWorldService = HttpRoutes.of[IO] { case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
    }

    createBlazeServer(
      ExecutionContext.fromExecutorService(Executors.newCachedThreadPool()),
      helloWorldService
    )

  }
  import cats.effect._
  import cats.syntax.all._
  import cats.implicits._

  private def createBlazeServer(
      executionContext: ExecutionContext,
      routes: HttpRoutes[IO]
  ): IO[FiberIO[Nothing]] = {
//    import zio.interop.catz._
//    import zio.interop.catz.implicits.rts
    //  import scala.concurrent.duration._
    BlazeServerBuilder[IO]
      .withExecutionContext(executionContext)
      .bindHttp(8080, "localhost")
      .withHttpWebSocketApp(_ => routes.orNotFound)
      .serve
      .compile
      .drain
      .foreverM
      .start
  }
}
