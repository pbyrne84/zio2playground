package com.github.pbyrne84.zio2playground.client

import com.github.pbyrne84.zio2playground.BaseSpec
import com.github.pbyrne84.zio2playground.testbootstrap.extensions.ClientOps
import com.github.pbyrne84.zio2playground.testbootstrap.wiremock.ServerAWireMock
import com.github.pbyrne84.zio2playground.tracing.{B3HTTPResponseTracing, TestZipkinTracer}
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.propagation.{TextMapGetter, TextMapPropagator}
import io.opentelemetry.extension.trace.propagation.B3Propagator
import zio.http._
import zio.telemetry.opentelemetry.Tracing
import zio.test._

import scala.jdk.CollectionConverters.SeqHasAsJava

object TracingClientSpec extends BaseSpec with ClientOps {

  val propagator: TextMapPropagator = B3Propagator.injectingMultiHeaders()

  val getter: TextMapGetter[List[Header]] = new TextMapGetter[List[Header]] {
    override def keys(carrier: List[Header]) = {
      carrier.map(_.headerName).asJava
    }

    override def get(carrier: List[Header], key: String): String = {
      carrier.find(_.headerName.toLowerCase == key.toLowerCase).map(_.renderedValue).orNull
    }
  }

  // look at  zio.telemetry.opentelemetry.Tracing
  // for span initialization
  override def spec =
    suite("tracing client")(
      test("add tracing to the headers") {

        val test = for {
          _ <- reset
          config <- getConfig
          _ <- ServerAWireMock.stubCall("sandwiches")
          response <- TracingClient
            .request(s"http://localhost:${config.serverAPort}/banana")
          responseText <- response.body.asString
          currentSpan <- Tracing.getCurrentSpan
          _ <- ServerAWireMock.verifyHeaders(
            List(
              Header.Custom(B3.header.traceId, currentSpan.getSpanContext.getTraceId),
              Header.Custom(
                B3.header.spanId,
                "[0-9a-f]{16}"
              ), // don't know span id has we have child spans within
              Header.Custom(B3.header.sampled, "1")
            )
          )
        } yield assertTrue(responseText == "sandwiches")

        import zio.telemetry.opentelemetry.TracingSyntax.OpenTelemetryZioOps

        test.spanFrom(propagator, List.empty, getter, "span-name", SpanKind.SERVER)

      }.provideSome[BaseSpec.Shared](
        zio.telemetry.opentelemetry.Tracing.live,
        TestZipkinTracer.live,
        TracingClient.tracingClientLayer,
        B3HTTPResponseTracing.layer,
        ZClient.default
      )
    )
}
