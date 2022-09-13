package com.github.pbyrne84.zio2playground.tracing

import com.github.pbyrne84.zio2playground.client.B3
import com.github.pbyrne84.zio2playground.logging.{ExampleLogAnnotations, TracingOps}
import io.opentelemetry.api.trace.{SpanKind, StatusCode, Tracer}
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.extension.trace.propagation.B3Propagator
import zhttp.http.Headers
import zio.ZIO
import zio.telemetry.opentelemetry.Tracing

object B3TracingOps {
  def defaultErrorMapper[E]: PartialFunction[E, StatusCode] = { case a =>
    StatusCode.UNSET
  }

  // Creates a new span and makes sure all the new details gets set in the logging context.
  // Very confusing just using Tracing.span otherwise.
  def serverSpan[R, E, A](name: String)(effect: => ZIO[R, E, A]): ZIO[R with Tracing, E, A] = {
    Tracing.span[R with Tracing, E, A](name, SpanKind.SERVER, defaultErrorMapper) {
      import TracingOps._

      for {
        span <- Tracing.getCurrentSpan
        spanContext = span.getSpanContext
        traceId = spanContext.getTraceId
        spanId = spanContext.getSpanId
        parentSpanId = span.maybeParentSpanId.getOrElse("???")
        snapName = span.maybeName.getOrElse("unknown")
        result <- effect @@ ExampleLogAnnotations.stringTraceId(traceId) @@
          ExampleLogAnnotations.parentSpanId(parentSpanId) @@
          ExampleLogAnnotations.stringSpanId(spanId) @@
          ExampleLogAnnotations.stringSpanName(snapName)

      } yield result
    }
  }

}

trait B3TracingOps {

  private val propagator: TextMapPropagator = B3Propagator.injectingMultiHeaders()

  private val getter: HeaderTextMapGetter = new HeaderTextMapGetter()

  implicit class B3Ops[A, B, C](operation: ZIO[A, B, C]) {

    // spanFrom operation
    import zio.telemetry.opentelemetry.TracingSyntax.OpenTelemetryZioOps

    def manualB3Trace(
        tracedId: String,
        spanId: String
    ): ZIO[A with Tracer with Tracing, B, C] = {
      operation
        .spanFrom(
          propagator = propagator,
          carrier = List(
            B3.header.traceId -> tracedId,
            B3.header.spanId -> spanId,
            B3.header.sampled -> "1"
          ),
          getter = getter,
          spanName = "span-name",
          spanKind = SpanKind.SERVER
        )
    }

    def headerB3Trace(headers: Headers): ZIO[A with Tracing, B, C] = {
      operation
        .spanFrom(
          propagator = propagator,
          carrier = headers.toList,
          getter = getter,
          spanName = "span-name",
          spanKind = SpanKind.SERVER
        )
    }
  }

}
