package com.github.pbyrne84.zio2playground.client

import com.github.pbyrne84.zio2playground.tracing.HTTPTracing
import zhttp.http.{Headers, HttpData, Method, Response}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.{Trace, ZIO, ZLayer}
import zio.telemetry.opentelemetry.Tracing

object B3 {
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

  val tracingClient: ZLayer[HTTPTracing, Nothing, TracingClient] = ZLayer {
    for {
      httpTracing <- ZIO.service[HTTPTracing]
    } yield new TracingClient(httpTracing)
  }

}

class TracingClient(HTTPTracing: HTTPTracing) {
  def request(
      url: String,
      method: Method = Method.GET,
      headers: Headers = Headers.empty,
      content: HttpData = HttpData.empty,
      ssl: ClientSSLOptions = ClientSSLOptions.DefaultSSL
  )(implicit
      trace: Trace
  ): ZIO[EventLoopGroup with ChannelFactory with Tracing, Throwable, Response] = {
    for {
      appendedHeaders <- HTTPTracing.appendHeaders(headers)
      response <- Client.request(
        url = url,
        method = method,
        headers = appendedHeaders,
        content = content,
        ssl = ssl
      )
    } yield response
  }
}
