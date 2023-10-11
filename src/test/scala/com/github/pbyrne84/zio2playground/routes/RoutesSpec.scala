package com.github.pbyrne84.zio2playground.routes

import com.github.pbyrne84.zio2playground.BaseSpec
import com.github.pbyrne84.zio2playground.BaseSpec.Shared
import com.github.pbyrne84.zio2playground.client.{B3, ExternalApiService, TracingClient}
import com.github.pbyrne84.zio2playground.config.ConfigReader
import com.github.pbyrne84.zio2playground.db.PersonRepo
import com.github.pbyrne84.zio2playground.testbootstrap.AllTestBootstrap
import com.github.pbyrne84.zio2playground.testbootstrap.extensions.ClientOps
import com.github.pbyrne84.zio2playground.testbootstrap.wiremock.ServerAWireMock
import com.github.pbyrne84.zio2playground.tracing.{B3HTTPResponseTracing, NonExportingTracer}
import io.opentelemetry.api.trace.Tracer
import org.mockito.Mockito
import org.slf4j.bridge.SLF4JBridgeHandler
import zio.http._
import zio.logging.backend.SLF4J
import zio.telemetry.opentelemetry.Tracing
import zio.test.TestAspect.{sequential, success}
import zio.test._
import zio.{Scope, URLayer, ZIO, ZLayer}

object RoutesSpec extends BaseSpec with ClientOps {
  // needed for util->sl4j logging
  SLF4JBridgeHandler.install()

  private val loggingLayer = zio.Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private def callService(
      request: Request
  ) = {
    ZIO
      .serviceWithZIO[Routes](_.routes.runZIO(request))
  }

  implicit class TestOps[B, C](
      zioOperation: ZIO[
        Shared
          with Tracing
          with B3HTTPResponseTracing
          with TracingClient
          with ExternalApiService
          with Scope
          with Client
          with Routes,
        B,
        C
      ]
  ) {
    // This is a bit joyous, needs to be added before the
    // .provideSome[BaseSpec.Shared]
    // call or get some fun errors.
    def provideCommonForTest: ZIO[Shared with PersonRepo, Any, C] = {
      // Can never find this thing as Tracing is a trait/object but finding the actual child implementation is
      // difficult as for some reason the implementation in Tracing.scoped breaks Intellij's navigation
      val tracingLive: URLayer[Tracer, Tracing] = zio.telemetry.opentelemetry.Tracing.live

      zioOperation.provideSome[Shared with PersonRepo](
        Routes.routesLayer,
        tracingLive,
        TracingClient.tracingClientLayer,
        NonExportingTracer.live,
        B3HTTPResponseTracing.layer,
        ExternalApiService.layer,
        ConfigReader.getRemoteServicesConfigLayer,
        Scope.default,
        ZClient.default,
        loggingLayer
      )
    }
  }

  override def spec = suite("routes")(
    suite("delete3")(
      test("should use mock for repo") {
        val request = Request.get(url = URL.empty.copy(path = Path.decode("/delete3")))
        val personRepoMock = Mockito.mock(classOf[PersonRepo])
        val personLayer = ZLayer.succeed(personRepoMock)

        val deleteCount = 1000L
        Mockito
          .when(personRepoMock.deletePeople())
          .thenReturn(ZIO.succeed(deleteCount))

        (for {
          _ <- reset
          result <- callService(request)
          content <- result.body.asString
        } yield assertTrue(
          content == deleteCount.toString
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

        (for {
          _ <- reset
          params <- AllTestBootstrap.getParams
          _ <- ServerAWireMock.stubCall(expected)
          url = s"http://localhost:${params.serverAPort}/made-up-path"
          response <- Client.request(url = url)
          dataAsString <- response.dataAsString
        } yield assertTrue(
          dataAsString == expected
        ))
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

        val traceIdHeader = Header.Custom(B3.header.traceId, traceId)
        val headersWithTrace = Headers(
          traceIdHeader,
          Header.Custom(B3.header.spanId, spanId),
          Header.Custom(B3.header.sampled, "1")
        )

        val id = 123
        val request =
          Request
            .get(url = URL.empty.copy(path = Path.decode(s"/proxy/$id")))
            .copy(
              headers = headersWithTrace
            )

        val test = for {
          _ <- reset
          _ <- ServerAWireMock.stubCall("fruit fly")
          response <- callService(request)
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
        val request = Request.get(url = URL.empty.copy(path = Path.decode(s"/proxy/$id")))

        val test = for {
          _ <- reset
          _ <- ServerAWireMock.stubCall("fruit fly")
          response <- callService(request)
          responseHeaders = response.headers.toList
          emptyTrace <- Tracing.getCurrentSpanContext
        } yield (
          assertTrue(
            // We cannot tell what the traceId will be, it just needs to be there and not the default invalid empty one.
            // Asserting something is not equal to something is always less than ideal due to the fact the basic premise can be
            // flawed so the test may never fail.
            // i.e may as well be
            // value != "pussycat"
            // some time in the future
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
  ).provideSome[BaseSpec.Shared](ZClient.default) @@ sequential @@ success

  private def getHeaderValue(headers: List[Header], key: String): Option[String] =
    headers.find(_.headerName == key).map(_.renderedValue)
}
