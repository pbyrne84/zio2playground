package com.github.pbyrne84.zio2playground.tracing

import com.github.pbyrne84.zio2playground.BaseSpec
import com.github.pbyrne84.zio2playground.client.B3
import io.opentelemetry.api.trace.{SpanKind, Tracer}
import zio.{Scope, ZIO}
import zio.telemetry.opentelemetry.Tracing
import zio.test._
object ManualTraceInitialisationSpec extends ZIOSpecDefault {

  implicit class B3Ops[A, B, C](operation: ZIO[A, B, C]) {
    def manualB3Trace(
        tracedId: String,
        spanId: String
    ): ZIO[A with Tracer with Tracing, B, C] = {
      // spanFrom extension method. Eye level is buy level.
      import zio.telemetry.opentelemetry.TracingSyntax.OpenTelemetryZioOps

      operation
        .spanFrom(
          propagator = B3Tracing.b3Propagator,
          carrier = List(
            B3.header.traceId -> tracedId,
            B3.header.spanId -> spanId,
            B3.header.sampled -> "1"
          ),
          getter = B3Tracing.headerTextMapGetter,
          spanName = "span-name",
          spanKind = SpanKind.SERVER
        )
    }
  }

  /*
    SdkSpanBuilder.startSpan() creates the span
    which is called by Tracing.scoped
   */
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("manually setting a trace")(
    test("should work using B3 headers") {
      val expectedTraceId = "01115d8eb7e102b505085969c4aca859"
      val expectedSpanId = "40ce80b7c43f2884"

      // handle access to get parent
      import com.github.pbyrne84.zio2playground.logging.TracingOps._

      (for {
        _ <- Tracing.setBaggage("foo", "far")
        currentContext <- Tracing.getCurrentContext
        currentSpan <- Tracing.getCurrentSpan
        spanDetails = currentSpan.maybeParentSpanIds
        _ <- ZIO.logInfo("current context: " + currentContext)
        _ <- ZIO.logInfo("current span id: " + currentSpan.getSpanContext.getSpanId)
      } yield assertTrue {
        spanDetails.contains(expectedTraceId -> (expectedSpanId))
      }).manualB3Trace(expectedTraceId, expectedSpanId)
        .provide(TestZipkinTracer.live, zio.telemetry.opentelemetry.Tracing.live, BaseSpec.logger)
    }
  )
}
