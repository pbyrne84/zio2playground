package com.github.pbyrne84.zio2playground.tracing

import com.github.pbyrne84.zio2playground.client.B3
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.{Context, ContextKey}
import zhttp.http.Headers
import zio.{URIO, ZIO, ZLayer}
import zio.telemetry.opentelemetry.Tracing

object HTTPTracing {

  val default: HTTPTracing = new HTTPTracing {
    override def appendHeaders(headers: Headers) = ZIO.succeed(headers)
  }
}

trait HTTPTracing {
  def appendHeaders(headers: Headers): ZIO[Tracing, Nothing, Headers]

  protected def currentContext: URIO[Tracing, Context] = Tracing.getCurrentContext
}

object B3HTTPTracing {
  val layer: ZLayer[Any, Nothing, B3HTTPTracing] = ZLayer(ZIO.succeed(new B3HTTPTracing))
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
