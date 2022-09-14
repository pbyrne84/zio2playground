package com.github.pbyrne84.zio2playground.testbootstrap.extensions

import zhttp.http.Response
import zio.ZIO

import java.nio.charset.StandardCharsets

object ClientOps extends ClientOps

trait ClientOps {

  implicit class ResponseOps(response: Response) {
    def dataAsString: ZIO[Any, Throwable, String] = {
      response.data.toByteBuf.map { byteBuf =>
        byteBuf.readCharSequence(byteBuf.readableBytes(), StandardCharsets.UTF_8).toString
      }
    }

  }
}
