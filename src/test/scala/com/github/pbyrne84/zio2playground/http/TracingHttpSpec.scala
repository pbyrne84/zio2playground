package com.github.pbyrne84.zio2playground.http

import com.github.pbyrne84.zio2playground.BaseSpec
import com.github.pbyrne84.zio2playground.client.{B3, ExternalApiService, TracingClient}
import com.github.pbyrne84.zio2playground.config.ConfigReader
import com.github.pbyrne84.zio2playground.testbootstrap.extensions.ClientOps
import com.github.pbyrne84.zio2playground.testbootstrap.wiremock.ServerAWireMock
import com.github.pbyrne84.zio2playground.tracing.{B3HTTPResponseTracing, NonExportingTracer}
import zhttp.http.{Headers, Http, Request, Response}
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.telemetry.opentelemetry.Tracing
import zio.test._
import zio.{Cause, ZIO, ZLayer}

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

        val traceIdHeader = B3.header.traceId -> traceId
        val headersWithTrace = Headers(
          traceIdHeader,
          B3.header.spanId -> spanId,
          // sampled controls whether things will be sent to zipkin or whatever.
          // The spanExporter in NonExportingTracer logs so we can observe this.
          // Though in production I think you should not rely on external sources passing
          // this value as the only thing that can be replied upon is being given the minimum
          // to get the desired response.
          //
          // There is also a debug header in
          // io.opentelemetry.extension.trace.propagation.B3PropagatorExtractorMultipleHeaders
          B3.header.sampled -> "1"
        )

        val request = Request(headers = headersWithTrace)
        val expectedHeaders = List(
          "content-type" -> "text/plain",
          "X-B3-TraceId" -> traceId
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
          ChannelFactory.auto,
          NonExportingTracer.live,
          EventLoopGroup.auto(0),
          ExternalApiService.layer,
          ConfigReader.getRemoteServicesConfigLayer,
          TracingClient.tracingClientLayer,
          com.github.pbyrne84.zio2playground.tracing.B3HTTPResponseTracing.layer
        )
      }
    )

  private def callTracingRoute(
      request: Request
  ) = {
    ZIO
      .serviceWithZIO[TracingRouteTestRoute](_.routesUsingExplicitPartialFunction.apply(request))
  }

}

class TracingRouteTestRoute {

  import ClientOps._

  val routesUsingExplicitPartialFunction: Http[
    EventLoopGroup
      with ChannelFactory
      with Tracing
      with TracingClient
      with ExternalApiService
      with B3HTTPResponseTracing,
    Throwable,
    Request,
    Response
  ] =
    TracingHttp.b3TracingHttp {
      routes
    }

  private def routes[R]: PartialFunction[Request, ZIO[
    R with EventLoopGroup
      with ChannelFactory
      with Tracing
      with TracingClient
      with ExternalApiService,
    Throwable,
    Response
  ]] = { case _ =>
    for {
      _ <- ZIO.logInfo("default route")
      result <- ExternalApiService.callApi(123)
      textResponse <- result.dataAsString
      _ <- ZIO.logInfo(s"response from service $textResponse")
    } yield {
      Response.text(s"I like $textResponse pizza")
    }
  }

}
