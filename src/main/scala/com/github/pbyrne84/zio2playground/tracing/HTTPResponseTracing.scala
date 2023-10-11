package com.github.pbyrne84.zio2playground.tracing

import com.github.pbyrne84.zio2playground.client.B3
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.{Context, ContextKey}
import zio.http.{Header, Headers}
import zio.telemetry.opentelemetry.Tracing
import zio.{URIO, ZIO, ZLayer}

object HTTPResponseTracing {}

trait HTTPResponseTracing {
  def appendHeadersToResponse(headers: Headers): ZIO[Tracing, Nothing, Headers]

  protected def currentContext: URIO[Tracing, Context] = Tracing.getCurrentContext
}

object B3HTTPResponseTracing {
  val layer: ZLayer[Any, Nothing, B3HTTPResponseTracing] = ZLayer(
    ZIO.succeed(new B3HTTPResponseTracing)
  )

  def appendHeadersToResponse(
      currentHeaders: Headers
  ): ZIO[Tracing with B3HTTPResponseTracing, Nothing, Headers] = {
    ZIO.serviceWithZIO[B3HTTPResponseTracing](_.appendHeadersToResponse(currentHeaders))
  }

}

class B3HTTPResponseTracing extends HTTPResponseTracing {
  override def appendHeadersToResponse(headers: Headers): ZIO[Tracing, Nothing, Headers] = {
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
        Header.Custom(B3.header.traceId, spanContext.getTraceId),
        Header.Custom(B3.header.spanId, spanContext.getSpanId),
        Header.Custom(B3.header.sampled, sampled)
      )
    )
  }
}
