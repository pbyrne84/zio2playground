package com.github.pbyrne84.zio2playground.testbootstrap.extensions

import zio.ZIO
import zio.http._

object ClientOps extends ClientOps

trait ClientOps {

  implicit class ResponseOps(response: Response) {
    def dataAsString: ZIO[Any, Throwable, String] = {
      response.body.asString
    }

  }
}
