package com.github.pbyrne84.zio2playground.tracing

import com.github.pbyrne84.zio2playground.client.B3
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.{Context, ContextKey}
import zhttp.http.Headers
import zio.telemetry.opentelemetry.Tracing
import zio.{URIO, ZIO, ZLayer}

object HTTPTracing {}

trait HTTPTracing {
  def appendHeaders(headers: Headers): ZIO[Tracing, Nothing, Headers]

  protected def currentContext: URIO[Tracing, Context] = Tracing.getCurrentContext
}

object B3HTTPTracing {
  val layer: ZLayer[Any, Nothing, B3HTTPTracing] = ZLayer(ZIO.succeed(new B3HTTPTracing))

  def appendHeaders(currentHeaders: Headers): ZIO[Tracing with B3HTTPTracing, Nothing, Headers] = {
    ZIO.serviceWithZIO[B3HTTPTracing](_.appendHeaders(currentHeaders))
  }

}

class B3HTTPTracing extends HTTPTracing {
  override def appendHeaders(headers: Headers): ZIO[Tracing, Nothing, Headers] = {
    currentContext.map(context => createHeadersFromContext(context, headers))
  }

  private def createHeadersFromContext(context: Context, headers: Headers): Headers = {
    val spanContext = Span.fromContext(context).getSpanContext

    val sampled =
      Option(context.get[Boolean](ContextKey.named("b3-debug")))
        .map(_ => "1")
        .getOrElse {
          if (spanContext.isSampled) "1" else "0"
        }

    headers.combine(
      Headers(
        B3.header.traceId -> spanContext.getTraceId,
        B3.header.spanId -> spanContext.getSpanId,
        B3.header.sampled -> sampled
      )
    )
  }
}
