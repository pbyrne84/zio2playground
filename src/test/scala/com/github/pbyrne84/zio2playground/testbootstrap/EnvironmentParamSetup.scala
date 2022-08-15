package com.github.pbyrne84.zio2playground.testbootstrap

import zio.{Ref, UIO, ZIO, ZLayer}

import java.net.ServerSocket
import scala.util.Using

object InitialisedParams {
  val layer = ZLayer(for {
    environmentParamSetup <- ZIO.service[EnvironmentParamSetup]
    initialisedParams <- environmentParamSetup.setup
  } yield initialisedParams)

  val empty: InitialisedParams = InitialisedParams("", "")

  val getParams: ZIO[EnvironmentParamSetup, Nothing, InitialisedParams] =
    EnvironmentParamSetup.setup

}
case class InitialisedParams(serverAPort: String, serverBPort: String) {
  val isEmpty: Boolean = serverAPort.isEmpty && serverBPort.isEmpty
}

object EnvironmentParamSetup {

  private val emptyParams: InitialisedParams = InitialisedParams("", "")
  private val a = 1

  val value = Ref.Synchronized.make(a)

  val layer: ZLayer[Any, Nothing, EnvironmentParamSetup] = ZLayer {
    for {
      maybeInitialisedParamsRef <- Ref.Synchronized.make(emptyParams)
      bb <- value
      _ <- bb.update(_ + 1)
      bbb <- bb.get
      _ = println(bbb)
      _ = println(maybeInitialisedParamsRef.hashCode())
    } yield new EnvironmentParamSetup(maybeInitialisedParamsRef)
  }

  def setup: ZIO[EnvironmentParamSetup, Nothing, InitialisedParams] = {
    ZIO.serviceWithZIO[EnvironmentParamSetup](_.setup)
  }

}

class EnvironmentParamSetup(
    maybeInitialisedParamsRef: Ref.Synchronized[InitialisedParams]
) {

  def setup: ZIO[Any, Nothing, InitialisedParams] = {
    for {
      maybeInitialisedParams <- maybeInitialisedParamsRef.modify { a: InitialisedParams =>
        if (a.isEmpty) {
          initialiseParams match {
            case Left(_) =>
              println("failed setting up params")
              (a, a)
            case Right(value) =>
              println(s"setting value $value")
              (value, value)
          }
        } else {
          println("found params")
          (a, a)
        }
      }
    } yield maybeInitialisedParams

  }

  private def initialiseParams: Either[Throwable, InitialisedParams] = {
    def serverPortCalculationOperation[A](
        mappingCall: (Int) => A
    ): Either[Throwable, A] = {
      Using(new ServerSocket(0)) { serverSocket: ServerSocket =>
        val result = mappingCall(serverSocket.getLocalPort)
        result
      }.toEither
    }

    for {
      serverAPort <- serverPortCalculationOperation { (port: Int) =>
        Option(System.getProperty("serverAPort")).getOrElse {
          System.setProperty("serverAPort", port.toString)
          port.toString
        }

      }
      serverBPort <- serverPortCalculationOperation { (port: Int) =>
        Option(System.getProperty("serverBPort")).getOrElse {
          System.setProperty("serverBPort", port.toString)
          port.toString
        }
      }
    } yield InitialisedParams(serverAPort, serverBPort)

  }

}
