package com.github.pbyrne84.zio2playground.http

import com.github.pbyrne84.zio2playground.BaseSpec
import com.github.pbyrne84.zio2playground.client.{B3, ExternalApiService, TracingClient}
import com.github.pbyrne84.zio2playground.config.ConfigReader
import com.github.pbyrne84.zio2playground.testbootstrap.extensions.ClientOps
import com.github.pbyrne84.zio2playground.testbootstrap.wiremock.ServerAWireMock
import com.github.pbyrne84.zio2playground.tracing.{B3HTTPResponseTracing, NonExportingTracer}
import zio.http._
import zio.telemetry.opentelemetry.Tracing
import zio.test._
import zio.{ZIO, ZLayer}

object TracingHttpSpec extends BaseSpec {
  private val testRouteLayer: ZLayer[Any, Nothing, TracingRouteTestRoute] = ZLayer {
    for {
      routes <- ZIO.succeed(new TracingRouteTestRoute)
    } yield routes
  }

  override def spec =
    suite("RouteAspect")(
      test(
        "should append trace to response when applied when partial function is applied explicitly"
      ) {
        val traceId = "00000000000000160000000000000016"
        val spanId = "000000000000029a"

        val traceIdHeader = Header.Custom(B3.header.traceId, traceId)
        val headersWithTrace = Headers(
          traceIdHeader,
          Header.Custom(B3.header.spanId, spanId),
          // sampled controls whether things will be sent to zipkin or whatever.
          // The spanExporter in NonExportingTracer logs so we can observe this.
          // Though in production I think you should not rely on external sources passing
          // this value as the only thing that can be replied upon is being given the minimum
          // to get the desired response.
          //
          // There is also a debug header in
          // io.opentelemetry.extension.trace.propagation.B3PropagatorExtractorMultipleHeaders
          Header.Custom(B3.header.sampled, "1")
        )

        val request = Request.get(URL.empty).copy(headers = headersWithTrace)
        val expectedHeaders = List(
          Header.Custom("content-type", "text/plain"),
          Header.Custom("X-B3-TraceId", traceId)
        )

        import ClientOps._

        (for {
          _ <- reset
          _ <- ServerAWireMock.stubCall("salami")
          response <- callTracingRoute(request)
          responseBody <- response.dataAsString
          responseHeaders = response.headers.toList
        } yield assertTrue(
          responseBody == "I like salami pizza",
          expectedHeaders.forall(responseHeaders.contains)
        )).provideSome[BaseSpec.Shared](
          testRouteLayer,
          zio.telemetry.opentelemetry.Tracing.live,
          NonExportingTracer.live,
          ExternalApiService.layer,
          ConfigReader.getRemoteServicesConfigLayer,
          TracingClient.tracingClientLayer,
          com.github.pbyrne84.zio2playground.tracing.B3HTTPResponseTracing.layer
        )
      }
    )

  private def callTracingRoute(
      request: Request
  ): ZIO[
    Tracing
      with TracingClient
      with ExternalApiService
      with B3HTTPResponseTracing
      with TracingRouteTestRoute,
    Option[Throwable],
    Response
  ] = {
    ZIO
      .serviceWithZIO[TracingRouteTestRoute](_.routesUsingExplicitPartialFunction.runZIO(request))
  }

}

class TracingRouteTestRoute {

  import ClientOps._

  val routesUsingExplicitPartialFunction: Http[
    Tracing with TracingClient with ExternalApiService with B3HTTPResponseTracing,
    Throwable,
    Request,
    Response
  ] =
    TracingHttp.b3TracingHttp {
      routes
    }

  private def routes[R]: PartialFunction[Request, ZIO[
    R with Tracing with TracingClient with ExternalApiService,
    Throwable,
    Response
  ]] = { case _ =>
    for {
      _ <- ZIO.logInfo("default route")
      result <- ExternalApiService
        .callApi(123)
        .provideSome[ExternalApiService with Tracing](
          TracingClient.tracingClientLayer,
          ZClient.default,
          B3HTTPResponseTracing.layer
        )
      textResponse <- result.dataAsString
      _ <- ZIO.logInfo(s"response from service $textResponse")
    } yield {
      Response.text(s"I like $textResponse pizza")
    }

  }
}
