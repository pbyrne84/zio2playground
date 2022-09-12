package com.github.pbyrne84.zio2playground.tracing

import com.github.pbyrne84.zio2playground.BaseSpec
import zio.{Scope, ZIO}
import zio.telemetry.opentelemetry.Tracing
import zio.test._
object ManualTraceInitialisationSpec extends ZIOSpecDefault with B3TracingOps {

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
