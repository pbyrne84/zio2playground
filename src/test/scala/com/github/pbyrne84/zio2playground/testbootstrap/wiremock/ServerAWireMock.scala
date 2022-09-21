package com.github.pbyrne84.zio2playground.testbootstrap.wiremock

import com.github.pbyrne84.zio2playground.testbootstrap.InitialisedParams
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.typesafe.scalalogging.StrictLogging
import zio.{Task, ZIO, ZLayer}

import scala.jdk.CollectionConverters.CollectionHasAsScala

object ServerAWireMock {

  val layer: ZLayer[InitialisedParams, Nothing, ServerAWireMock] = ZLayer {
    for {
      initialisedParams <- ZIO.service[InitialisedParams]
      testWireMock <- ZIO.succeed(new TestWireMock(initialisedParams.serverAPort.toInt))
    } yield new ServerAWireMock(testWireMock)
  }

  def stubCall(response: String): ZIO[ServerAWireMock, Throwable, Unit] =
    ZIO.serviceWithZIO[ServerAWireMock](_.stubCall(response))

  def getStubbings: ZIO[ServerAWireMock, Throwable, List[StubMapping]] =
    ZIO.serviceWithZIO[ServerAWireMock](_.getStubbings)

  def verifyHeaders(headers: List[(String, String)]): ZIO[ServerAWireMock, Throwable, Unit] = {
    ZIO.serviceWithZIO[ServerAWireMock](_.verifyHeaders(headers))
  }

  def reset: ZIO[ServerAWireMock, Throwable, Unit] = ZIO.serviceWithZIO[ServerAWireMock](_.reset)

}

class ServerAWireMock(testWireMock: TestWireMock) extends StrictLogging {
  import WireMock._
  // As this is part of a shared service layer this should only fire once.
  // Read the readme about why this may not be the case (object.main).
  logger.info("creating ServerAWireMock")

  def reset: Task[Unit] =
    testWireMock.reset

  def stubCall(response: String): Task[Unit] = {

    ZIO.attempt {
      testWireMock.wireMock.stubFor(
        WireMock
          .any(WireMock.urlMatching(".*"))
          .willReturn(aResponse().withBody(response))
      )
    }
  }

  def verifyHeaders(headers: List[(String, String)]): Task[Unit] = {
    // wiremock is a builder builder builder mutation thingy
    val builder: RequestPatternBuilder = anyRequestedFor(urlMatching(".*"))
    val verification = headers.foldLeft(builder) { case (request, (name, value)) =>
      request.withHeader(name, matching(value))
    }

    ZIO.attempt(testWireMock.wireMock.verify(verification))
  }

  def getStubbings: Task[List[StubMapping]] = {
    ZIO.attemptBlocking { testWireMock.wireMock.getStubMappings.asScala.toList }
  }

}
