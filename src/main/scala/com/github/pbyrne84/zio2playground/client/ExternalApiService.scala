package com.github.pbyrne84.zio2playground.client

import com.github.pbyrne84.zio2playground.config.{ConfigReader, RemoteServicesConfig}
import com.github.pbyrne84.zio2playground.tracing.B3Tracing
import zio.http.{Body, Headers, Method}
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

  def callApi(id: Int) = {
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
          content = Body.fromString(payload)
        )
        _ = result.body
      } yield (
        result
      )
    }

  }
}
