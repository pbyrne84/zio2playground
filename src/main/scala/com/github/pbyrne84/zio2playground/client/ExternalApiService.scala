package com.github.pbyrne84.zio2playground.client

import com.github.pbyrne84.zio2playground.config.{ConfigReader, RemoteServicesConfig}
import com.github.pbyrne84.zio2playground.tracing.B3Tracing
import zhttp.http.{Headers, HttpData, Method, Response}
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.telemetry.opentelemetry.Tracing
import zio.{ZIO, ZLayer}

object ExternalApiService {

  val layer: ZLayer[RemoteServicesConfig, Nothing, ExternalApiService] = ZLayer {
    for {
      config <- ZIO.service[RemoteServicesConfig]
      format = config.serverA.stripSuffix("/") + "/downstream/%s"
    } yield new ExternalApiService(format)
  }

  val live: ZLayer[Any, Throwable, ExternalApiService] = ZLayer(
    ZIO
      .service[ExternalApiService]
      .provide(
        layer,
        ConfigReader.getRemoteServicesConfigLayer
      )
  )

  def callApi(id: Int) = {
    ZIO.serviceWithZIO[ExternalApiService](_.callApi(id))
  }

}

class ExternalApiService(urlFormat: String) {

  def callApi(id: Int): ZIO[
    EventLoopGroup with ChannelFactory with Tracing with TracingClient,
    Throwable,
    Response
  ] = {
    val payload =
      s"""
        |{ "id" : $id}
        |""".stripMargin

    val url = urlFormat.format(id)

    B3Tracing.serverSpan("ExternalApiService.callApi") {
      for {
        _ <- ZIO.logInfo(s"calling $url")
        result <- TracingClient.request(
          url = url,
          method = Method.POST,
          headers = Headers("Content-Type" -> "application/json"),
          content = HttpData.fromString(payload)
        )

        _ = result.data
      } yield (
        result
      )
    }

  }
}
