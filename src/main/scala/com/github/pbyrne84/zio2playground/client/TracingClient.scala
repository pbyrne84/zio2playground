package com.github.pbyrne84.zio2playground.client

import com.github.pbyrne84.zio2playground.logging.ExampleLogAnnotations
import com.github.pbyrne84.zio2playground.tracing.{B3Tracing, HTTPResponseTracing}
import io.opentelemetry.api.trace.{SpanId, TraceId}
import zhttp.http.{Headers, HttpData, Method, Response}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.telemetry.opentelemetry.Tracing
import zio.{Trace, ZIO, ZLayer}

object B3 {

  // current context returns this as filler if the tracing is not working properly
  val emptyTraceId: String = "0".padTo(TraceId.getLength, "0").mkString
  val emptySpanId: String = "0".padTo(SpanId.getLength, "0").mkString

  object header {
    val traceId: String = "X-B3-TraceId"
    val spanId: String = "X-B3-SpanId"
    val sampled: String = "X-B3-Sampled"
  }
}

object TracingClient {
  def request(
      url: String,
      method: Method = Method.GET,
      headers: Headers = Headers.empty,
      content: HttpData = HttpData.empty,
      ssl: ClientSSLOptions = ClientSSLOptions.DefaultSSL
  ): ZIO[
    EventLoopGroup with ChannelFactory with Tracing with TracingClient,
    Throwable,
    Response
  ] = {
    ZIO.service[TracingClient].flatMap(_.request(url, method, headers, content, ssl))
  }

  val tracingClientLayer: ZLayer[HTTPResponseTracing, Nothing, TracingClient] = ZLayer {
    for {
      httpTracing <- ZIO.service[HTTPResponseTracing]
    } yield new TracingClient(httpTracing)
  }

}

class TracingClient(HTTPTracing: HTTPResponseTracing) {
  def request(
      url: String,
      method: Method = Method.GET,
      headers: Headers = Headers.empty,
      content: HttpData = HttpData.empty,
      ssl: ClientSSLOptions = ClientSSLOptions.DefaultSSL
  ): ZIO[EventLoopGroup with ChannelFactory with Tracing, Throwable, Response] =
    B3Tracing.serverSpan(s"${method.toString.toLowerCase}-client-call") {
      for {
        _ <- ZIO
          .logInfo(s"calling remote service")
        appendedHeaders <- HTTPTracing.appendHeadersToResponse(headers)
        response <- Client.request(
          url = url,
          method = method,
          headers = appendedHeaders,
          content = content,
          ssl = ssl
        )
        _ <- B3Tracing.serverSpan("client-call-status") {
          // we can now look for things that are not okay using span_name = client-call-status and message != OK as a search value
          // we should also log the payload we sent at that point.
          ZIO.logInfo(response.status.toString)
        }
      } yield response
    } @@ ExampleLogAnnotations.clientRequest(method, url)
}
