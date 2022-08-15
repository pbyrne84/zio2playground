package com.github.pbyrne84.zio2playground.tracing

import com.github.pbyrne84.zio2playground.client.B3
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.propagation.{TextMapGetter, TextMapPropagator}
import io.opentelemetry.extension.trace.propagation.B3Propagator
import zhttp.http.Header
import zio.Scope
import zio.telemetry.opentelemetry.Tracing
import zio.test._

import scala.jdk.CollectionConverters.SeqHasAsJava
object ManualTraceInitialisationSpec extends ZIOSpecDefault {

  private val propagator: TextMapPropagator = B3Propagator.injectingMultiHeaders()

  private val getter: TextMapGetter[List[Header]] = new TextMapGetter[List[Header]] {
    override def keys(carrier: List[Header]) = {
      println(s"keys $carrier ")
      carrier.map(_._1.toString).asJava
    }

    // This will be called B3PropagatorExtractorMultipleHeaders using the values passed in
    // the header list in test.spanFrom.
    // At the moment sampled header need to be there which is a bit annoying as you can hack adding the
    // header all the time in the route but that is pants. There should be no faith in any calling entity
    // to be correct.
    // So if the 3 headers are not there or have incorrect values then we get varied results from traced ids that are invalid
    // (00000000000000000000000000000000 and 0000000000000000) or are not set at all.
    override def get(carrier: List[Header], key: String): String = {
      println(s"get $carrier -  $key")
      carrier.find(_._1.toString == key).map(_._2.toString).orNull
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

      val test =
        for {
          _ <- Tracing.setBaggage("foo", "far")
          currentContext <- Tracing.getCurrentContext
          currentSpan <- Tracing.getCurrentSpan
          spanDetails = currentSpan.maybeParentSpanIds
          _ = println(currentContext)
        } yield assertTrue {
          spanDetails.contains(expectedTraceId -> (expectedSpanId))
        }

      // Extension methods for spanFrom etc to actually add the values
      // The method to process it all is a bit disjointed as the core
      // library is java so background mutation is the key
      import zio.telemetry.opentelemetry.TracingSyntax.OpenTelemetryZioOps
      test.spanFrom(
        propagator = propagator,
        carrier = List(
          B3.header.traceId -> expectedTraceId,
          B3.header.spanId -> expectedSpanId,
          B3.header.sampled -> "1"
        ),
        getter = getter,
        spanName = "span-name",
        spanKind = SpanKind.SERVER
      )

    }.provide(TestZipkinTracer.live, zio.telemetry.opentelemetry.Tracing.live)
  )
}
