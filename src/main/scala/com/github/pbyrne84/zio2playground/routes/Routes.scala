package com.github.pbyrne84.zio2playground.routes

import com.github.pbyrne84.zio2playground.Builds
import com.github.pbyrne84.zio2playground.Builds.RoutesBuild
import com.github.pbyrne84.zio2playground.client.{ExternalApiService, TracingClient}
import com.github.pbyrne84.zio2playground.db.PersonRepo
import com.github.pbyrne84.zio2playground.tracing.{
  B3HTTPResponseTracing,
  B3Tracing,
  NonExportingTracer
}
import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{ChannelFactory, EventLoopGroup, Server}
import zio._
import zio.logging.backend.SLF4J
import zio.telemetry.opentelemetry.Tracing

import scala.util.Try

object Routes extends ZIOAppDefault {
  // Set a port
  private val PORT = 58479

  val routesLayer: ZLayer[PersonRepo, Nothing, Routes] = ZLayer {
    for {
      personRepo <- ZIO.service[PersonRepo]
    } yield (new Routes(personRepo))
  }

  private val loggingLayer = zio.Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val run = RoutesBuild.routesBuild.flatMap { routes: Routes =>

    val server =
      Server.port(PORT) ++ // Setup port
        Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
        Server.app(routes.routes) // Setup the Http routes

  ZIOAppArgs.getArgs.flatMap { args =>
    // Configure thread count using CLI
    val nThreads: Int = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(0)

    // Create a new server
    server.make
      .flatMap((start: Server.Start) =>
        // Waiting for the server to start
        Console.printLine(s"Server started on port ${start.port}")

        // Ensures the server doesn't die after printing
          *> ZIO.never,
      )
      .provide(
        ServerChannelFactory.auto,
        EventLoopGroup.auto(nThreads),
        Scope.default,
        ExternalApiService.live,
        ChannelFactory.auto,
        zio.telemetry.opentelemetry.Tracing.live,
        TracingClient.tracingClientLayer,
        NonExportingTracer.live,
        B3HTTPResponseTracing.layer,
        loggingLayer
      )
  }

  }

}

class Routes(personRepo: PersonRepo) {

  val routes = Http.collectZIO[Request] {
    // int() is hiding in the RouteDecoderModule, could not find it in any examples but I may be blind
    case req @ Method.GET -> !! / "proxy" / int(id) =>
      callTracedService(req, id)

    case Method.GET -> !! / "delete1" =>
      Builds.PersonServiceBuild.personServiceBuild
        .flatMap(_.deletePeople().map((deleteCount: Long) => Response.text(deleteCount.toString)))

    case Method.GET -> !! / "delete2" =>
      PersonRepo
        .deletePeople()
        .map((deleteCount: Long) => Response.text(deleteCount.toString))
        .provideLayer(Builds.PersonRepoBuild.personRepoMake)

    case Method.GET -> !! / "delete3" =>
      personRepo
        .deletePeople()
        .map((deleteCount: Long) => Response.text(deleteCount.toString))

  }

  // TracingHttp in com.github.pbyrne84.zio2playground.http does all the add tracing stuff in theory.
  // it takes a Http.collectZIO[Request] compatible format of
  // routes: PartialFunction[Request, ZIO[R, E, Response]] and
  // just intercepts the request before the call to do the processing.
  private def callTracedService(req: Request, id: RuntimeFlags): ZIO[
    Tracing
      with B3HTTPResponseTracing
      with EventLoopGroup
      with ChannelFactory
      with TracingClient
      with ExternalApiService,
    Throwable,
    Response
  ] = {
    B3Tracing
      .requestInitialisationSpan("callTracedService", req) {
        for {
          _ <- ZIO.logInfo(s"received a called traced service call with the id $id")
          result <- ExternalApiService
            .callApi(id)
          headersWithTracing <- B3HTTPResponseTracing.appendHeadersToResponse(
            Headers(HeaderNames.contentType, HeaderValues.textPlain)
          )
        } yield {
          Response.text(result.data.toString).copy(headers = headersWithTracing)
        }
      }
  }

}
