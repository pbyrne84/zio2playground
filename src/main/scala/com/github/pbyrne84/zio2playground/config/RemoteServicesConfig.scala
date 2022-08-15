package com.github.pbyrne84.zio2playground.config

object RemoteServicesConfig {

  import zio.config._
  import ConfigDescriptor._

  val remoteServicesDescriptor: _root_.zio.config.ConfigDescriptor[RemoteServicesConfig] =
    nested("remoteServers") {
      string("serverA") zip string("serverB")
    }.to[RemoteServicesConfig]

}

case class RemoteServicesConfig(serverA: String, serverB: String)
