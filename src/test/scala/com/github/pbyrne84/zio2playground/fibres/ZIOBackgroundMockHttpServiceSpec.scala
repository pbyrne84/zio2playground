package com.github.pbyrne84.zio2playground.fibres

import com.github.pbyrne84.zio2playground.BaseSpec
import com.github.pbyrne84.zio2playground.BaseSpec.Shared
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.test._
import zio.{FiberRefs, Runtime, RuntimeFlags, Scope, ZEnvironment, ZIO}

object ZioTestHttpClient {

  // too many Responses in one project
  def call(urlText: String): ZIO[Any, Throwable, sttp.client3.Response[String]] = {
    import sttp.client3._ // all the basicRequest/asStringAlways stuff etc.
    for {
      backend <- HttpClientZioBackend()
      url = uri"$urlText"
      request = basicRequest
        .response(asStringAlways)
        .get(url)
      response <- backend
        .send(request)
    } yield response
  }
}

// Experimenting to see if I can create pluggable backends for
// https://github.com/pbyrne84/scalahttpmock
// Hard coding reliance on either IO or ZIO can leave projects
// with dependency conflicts on their major dependencies.
//
//
object ZIOBackgroundMockHttpServiceSpec extends BaseSpec {
  override def spec: Spec[Shared with TestEnvironment with Scope, Any] = {
    suite(getClass.getSimpleName)(
      suite("creating a background http service should")(
        test("not interfere with the code under test") { // all the basicRequest/asStringAlways stuff etc.
          val operation = new ZioCustomOperation()
          for {
            backoundFork <- operation.run
            _ <- operation.createStartupWaitingWebService
            //  _ = Thread.sleep(1000)
            meow <- ZIO
              .succeed(println("should shut down"))
            response <- ZioTestHttpClient.call("http://localhost:8080/text")
            _ = println(response)
            _ = println("boop")
            _ <- ZIO.attempt(Thread.sleep(1000))
            _ <- ZIO.succeed(println("ssss"))
            // _ <- backoundFork.interruptFork
          } yield assertTrue(true)
        }
      )
    )
  }
}

object ZioCustomOperation {
  val customRuntime: Runtime[Any] =
    Runtime(ZEnvironment.empty, FiberRefs.empty, RuntimeFlags.default)

  def main(args: Array[String]): Unit = {
    new IOCustomOperation().run
  }
}

//Just create something that will run in the background that cannot inhibit test
class ZioCustomOperation {

  def run = {
    val call = (for {
      counters <- ZIO.succeed((0 to 3000).toList)
      x <- ZIO.foreach(counters) { counter =>
        ZIO
          .blocking { // blocking as there is a Thread.sleep within.
            ZIO.succeed {
              Thread.sleep(100)
              println(counter)
              counter
            }
          }
      }
    } yield ()).fork

    call
  }

  def createStartupWaitingWebService = {

    for {
      _ <- createBackgroundWebService
      a <- (for {
        _ <- ZioTestHttpClient
          .call("http://localhost:8080/text")
        _ = Thread.sleep(
          100
        ) // I actually want a real clock for scheduling etc but in a test there is a test clock
      } yield ()).retryN(1000)
    } yield a
  }

  private def createBackgroundWebService = {
    import zio.http._
    import zio.http.model.Method

    val app: HttpApp[Any, Nothing] = Http.collect[Request] { case Method.GET -> !! / "text" =>
      Response.text("Hello World!")
    }

    Server.serve(app).provide(Server.default).fork

  }
}
