package com.github.pbyrne84.zio2playground.routes

import com.github.pbyrne84.zio2playground.BaseSpec
import com.github.pbyrne84.zio2playground.BaseSpec.Shared
import com.github.pbyrne84.zio2playground.client.{B3, ExternalApiService, TracingClient}
import com.github.pbyrne84.zio2playground.config.ConfigReader
import com.github.pbyrne84.zio2playground.db.PersonRepo
import com.github.pbyrne84.zio2playground.routes.RoutesSpec.getHeaderValue
import com.github.pbyrne84.zio2playground.testbootstrap.AllTestBootstrap
import com.github.pbyrne84.zio2playground.testbootstrap.extensions.ClientOps
import com.github.pbyrne84.zio2playground.testbootstrap.wiremock.ServerAWireMock
import com.github.pbyrne84.zio2playground.tracing.{
  B3HTTPTracing,
  B3TracingOps,
  NonExportingB3Tracer,
  TestZipkinTracer
}
import org.mockito.Mockito
import org.slf4j.bridge.SLF4JBridgeHandler
import zhttp.http._
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.logging.backend.SLF4J
import zio.telemetry.opentelemetry.Tracing
import zio.test.TestAspect.{sequential, success}
import zio.test._
import zio.{Clock, Random, Scope, ZIO, ZLayer}

object RoutesSpec extends BaseSpec with ClientOps with B3TracingOps {
  // needed for util->sl4j logging
  SLF4JBridgeHandler.install()

  val loggingLayer = zio.Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private def routes(
      request: Request
  ) = {
    ZIO
      .serviceWithZIO[Routes](_.routes.apply(request))
  }

  implicit class TestOps[B, C](
      zioOperation: ZIO[
        Shared
          with Tracing
          with B3HTTPTracing
          with EventLoopGroup
          with ChannelFactory
          with TracingClient
          with ExternalApiService
          with Scope
          with Routes,
        B,
        C
      ]
  ) {
    // This is a bit joyous, needs to be added before the
    // .provideSome[BaseSpec.Shared]
    // call or get some fun errors.
    def provideCommonForTest: ZIO[Shared with PersonRepo, Any, C] = {
      zioOperation.provideSome[Shared with PersonRepo](
        Routes.routesLayer,
        ChannelFactory.auto,
        zio.telemetry.opentelemetry.Tracing.live,
        TracingClient.tracingClientLayer,
        NonExportingB3Tracer.live,
        B3HTTPTracing.layer,
        EventLoopGroup.auto(0),
        ExternalApiService.layer,
        ConfigReader.getRemoteServicesConfigLayer,
        Scope.default,
        loggingLayer
      )
    }
  }

  override def spec = suite("routes")(
    suite("delete3")(
      test("should use mock for repo") {
        val request = Request(url = URL.empty.setPath("/delete3"))
        val personRepoMock = Mockito.mock(classOf[PersonRepo])
        val personLayer = ZLayer.succeed(personRepoMock)

        val deleteCount = 1000L
        Mockito
          .when(personRepoMock.deletePeople())
          .thenReturn(ZIO.succeed(deleteCount))

        (for {
          _ <- reset
          result <- routes(request)
        } yield assertTrue(
          result.data == HttpData.fromString(deleteCount.toString)
        )).provideCommonForTest
          .provideSome[BaseSpec.Shared](
            personLayer
          )

      }
    ),
    suite("client test")(
      test(
        "should get response from wiremock"
      ) {
        val expected = "empty calories are the best"

        // EventLoopGroup with ChannelFactory with ServerAWireMock with AllTestBootstrap with Shared
        (for {
          _ <- reset
          params <- AllTestBootstrap.getParams
          _ <- ServerAWireMock.stubCall(expected)
          url = s"http://localhost:${params.serverAPort}/made-up-path"
          response <- Client.request(url = url)
          // verify we have sent the header downstream
          dataAsString <- response.dataAsString
        } yield assertTrue(
          dataAsString == expected
        )).provideSome[BaseSpec.Shared](EventLoopGroup.auto(), ChannelFactory.auto)
      }
    ),
    suite("calling proxy route")(
      test("should initialise B3 trace ids if they passed in the header") {
        val personRepoMock = Mockito.mock(classOf[PersonRepo])
        val personLayer = ZLayer.succeed(personRepoMock)

        // io.opentelemetry.api.trace.TraceId.fromLongs(22, 22)
        val traceId = "00000000000000160000000000000016"

        // io.opentelemetry.api.trace.SpanId.fromLong(666))
        val spanId = "000000000000029a"

        val traceIdHeader = B3.header.traceId -> traceId
        val headersWithTrace = Headers(
          traceIdHeader,
          B3.header.spanId -> spanId,
          B3.header.sampled -> "1"
        )

        val id = 123
        val request =
          Request(url = URL.empty.setPath(path = s"/proxy/$id"), headers = headersWithTrace)

        val test = for {
          _ <- reset
          _ <- ServerAWireMock.stubCall("fruit fly")
          response <- routes(request)
          responseHeaders = response.headers.toList
          maybeNewSpanId = getHeaderValue(responseHeaders, B3.header.spanId)
          _ <- ServerAWireMock.verifyHeaders(List(traceIdHeader))
        } yield {

          assertTrue(
            getHeaderValue(responseHeaders, B3.header.traceId).contains(traceId),
            // we want a span id but not the one passed in as the new one should be auto generated
            // as it is a child event
            // Switching in a
            // ZLayer(ZIO.attempt(TracerProvider.noop().tracerBuilder("").build()))
            // Instead of a
            // NonExportingB3Tracer.live
            // will stop new span ids being generated breaking this test
            maybeNewSpanId.isDefined,
            !maybeNewSpanId.contains(spanId)
          )
        }

        test.provideCommonForTest
          .provideSome[BaseSpec.Shared](
            personLayer
          )

      },
      test("create a trace id where there is not one") {
        val personRepoMock = Mockito.mock(classOf[PersonRepo])
        val personLayer = ZLayer.succeed(personRepoMock)
        val id = 433
        val request = Request(url = URL.empty.setPath(path = s"/proxy/$id"))

        val test = for {
          _ <- reset
          _ <- ServerAWireMock.stubCall("fruit fly")
          response <- routes(request)
          responseHeaders = response.headers.toList
          emptyTrace <- Tracing.getCurrentSpanContext
        } yield (
          assertTrue(
            // We cannot tell what the traceId will be, it just needs to be there an not the default invalid empty one.
            // Asserting something is not equal to something is always less than ideal due to the basic premise can be
            // flawed so test may never fail.
            // i.e may as well be
            // value != "pussycat"
            getHeaderValue(responseHeaders, B3.header.traceId).isDefined,
            !getHeaderValue(responseHeaders, B3.header.traceId).contains(B3.emptyTraceId),
            getHeaderValue(responseHeaders, B3.header.spanId).isDefined,
            !getHeaderValue(responseHeaders, B3.header.spanId).contains(B3.emptySpanId),
            // Not really needed but verifies assumptions about what is an empty trace etc are correct.
            // As I say anything tests involving not equal are less than ideal and preferably avoided.
            B3.emptyTraceId == emptyTrace.getTraceId,
            B3.emptySpanId == emptyTrace.getSpanId
          )
        )

        test.provideCommonForTest
          .provideSome[BaseSpec.Shared](
            personLayer
          )
      }
    )
  ) @@ sequential @@ success

  private def getHeaderValue(headers: List[Header], key: String): Option[String] =
    headers.find(_._1 == key).map(_._2.toString)
}
