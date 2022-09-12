package com.github.pbyrne84.zio2playground.testbootstrap

import com.typesafe.scalalogging.StrictLogging
import zio.{Ref, ZIO, ZLayer}

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
  val value = Ref.Synchronized.make(1)

  val layer: ZLayer[Any, Nothing, EnvironmentParamSetup] = ZLayer {
    for {
      maybeInitialisedParamsRef <- Ref.Synchronized.make(emptyParams)
    } yield new EnvironmentParamSetup(maybeInitialisedParamsRef)
  }

  def setup: ZIO[EnvironmentParamSetup, Nothing, InitialisedParams] = {
    ZIO.serviceWithZIO[EnvironmentParamSetup](_.setup)
  }

}

class EnvironmentParamSetup(
    maybeInitialisedParamsRef: Ref.Synchronized[InitialisedParams]
) extends StrictLogging {

  def setup: ZIO[Any, Nothing, InitialisedParams] = {
    for {
      // As mentioned in the read me depending on how you run the test affects all the ref stuff.
      // If running via main as the tests all are objects so the default in Intellij with the plugin
      // then everything seems to initialise 3 times versus 1. Kooky.
      maybeInitialisedParams <- maybeInitialisedParamsRef.modify { a: InitialisedParams =>
        if (a.isEmpty) {
          initialiseParams match {
            case Left(_) =>
              logger.info("failed setting up params")
              (a, a)
            case Right(value) =>
              logger.info(s"setting value $value")
              (value, value)
          }
        } else {
          logger.info("found params")
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
