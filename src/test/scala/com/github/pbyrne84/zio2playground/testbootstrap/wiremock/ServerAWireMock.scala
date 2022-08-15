package com.github.pbyrne84.zio2playground.testbootstrap.wiremock

import com.github.pbyrne84.zio2playground.testbootstrap.InitialisedParams
import com.github.tomakehurst.wiremock.client.WireMock
import zio.{Task, ZIO, ZLayer}

object ServerAWireMock {

  val layer: ZLayer[InitialisedParams, Nothing, ServerAWireMock] = ZLayer {
    for {
      initialisedParams <- ZIO.service[InitialisedParams]
      testWireMock <- ZIO.succeed(new TestWireMock(initialisedParams.serverAPort.toInt))
    } yield new ServerAWireMock(testWireMock)
  }

  def stubCall(response: String): ZIO[ServerAWireMock, Throwable, Unit] =
    ZIO.serviceWithZIO[ServerAWireMock](_.stubCall(response))

  def getStubbings: ZIO[ServerAWireMock, Throwable, Unit] =
    ZIO.serviceWithZIO[ServerAWireMock](_.getStubbings)

  def verifyHeaders(headers: List[(String, String)]) = {
    ZIO.serviceWithZIO[ServerAWireMock](_.verifyHeaders(headers))
  }

  def reset: ZIO[ServerAWireMock, Throwable, Unit] = ZIO.serviceWithZIO[ServerAWireMock](_.reset)

}

class ServerAWireMock(testWireMock: TestWireMock) {
  import WireMock._
  println("creating ServerAWireMock")

  def reset: Task[Unit] =
    testWireMock.reset

  def stubCall(response: String): Task[Unit] = {

    ZIO.attempt {
      testWireMock.wireMock.stubFor(
        WireMock
          .any(WireMock.urlMatching(".*"))
          .willReturn(aResponse().withBody(response))
      )
      println("doing the stubbing")
    }
  }

  def verifyHeaders(headers: List[(String, String)]): Task[Unit] = {
    val verification = headers.foldLeft(getRequestedFor(urlMatching(".*"))) {
      case (request, (name, value)) =>
        request.withHeader(name, equalTo(value))

    }

    ZIO.attempt {
      testWireMock.wireMock.verify(verification)

    }

  }

  def getStubbings: Task[Unit] = {
    ZIO.attemptBlocking {
      println("boooop " + testWireMock.wireMock.getStubMappings)
    }
  }

}
