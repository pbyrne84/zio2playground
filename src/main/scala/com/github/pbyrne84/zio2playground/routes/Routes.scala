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
import zio.ZLayer.ZLayerProvideSomeOps
import zio._
import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.NettyConfig.LeakDetectionLevel
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

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    RoutesBuild.routesBuild.flatMap(routes => startService(routes))
  }

  private def startService(routes: Routes): ZIO[ZIOAppArgs, Any, Nothing] = {
    ZIOAppArgs.getArgs.flatMap { args =>
      // Configure thread count using CLI
      val nThreads: Int = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(0)

      val layeredRoutes =
        routes.routes.mapError { e =>
          Response(
            Status.InternalServerError,
            body = Body.fromString(s"I haz problem ${e.toString}")
          )
        }

      val config = Server.Config.default
        .port(PORT)
      val nettyConfig = NettyConfig.default
        .leakDetection(LeakDetectionLevel.PARANOID)
        .maxThreads(nThreads)
      val configLayer = ZLayer.succeed(config)
      val nettyConfigLayer = ZLayer.succeed(nettyConfig)

      (Server.install(layeredRoutes).flatMap { port =>
        Console.printLine(s"Started server on port: $port")
      } *> ZIO.never)
        .provide(
          configLayer,
          nettyConfigLayer,
          Server.customized,
          Scope.default,
          ExternalApiService.live,
          zio.telemetry.opentelemetry.Tracing.live,
          TracingClient.tracingClientLayer,
          NonExportingTracer.live,
          B3HTTPResponseTracing.layer,
          ZClient.default,
          loggingLayer
        )
    }
  }
}

class Routes(personRepo: PersonRepo) {

  val routes: Http[
    Tracing
      with B3HTTPResponseTracing
      with TracingClient
      with ExternalApiService
      with Client
      with Any
      with Scope,
    Throwable,
    Request,
    Response
  ] = Http.collectZIO[Request] {
    // int() is hiding in the RouteDecoderModule, could not find it in any examples but I may be blind
    case req @ Method.GET -> Root / "proxy" / int(id) =>
      callTracedService(req, id)

    case Method.GET -> Root / "delete1" =>
      Builds.PersonServiceBuild.personServiceBuild
        .flatMap(_.deletePeople().map((deleteCount: Long) => Response.text(deleteCount.toString)))

    case Method.GET -> Root / "delete2" =>
      PersonRepo
        .deletePeople()
        .map((deleteCount: Long) => Response.text(deleteCount.toString))
        .provideLayer(Builds.PersonRepoBuild.personRepoMake)

    case Method.GET -> Root / "delete3" =>
      personRepo
        .deletePeople()
        .map((deleteCount: Long) => Response.text(deleteCount.toString))

  }

  // TracingHttp in com.github.pbyrne84.zio2playground.http does all the add tracing stuff in theory.
  // it takes a Http.collectZIO[Request] compatible format of
  // routes: PartialFunction[Request, ZIO[R, E, Response]] and
  // just intercepts the request before the call to do the processing.
  private def callTracedService(req: Request, id: RuntimeFlags): ZIO[
    Tracing with B3HTTPResponseTracing with TracingClient with ExternalApiService with Client,
    Throwable,
    Response
  ] = {
    B3Tracing
      .requestInitialisationSpan("callTracedService", req) {
        for {
          _ <- ZIO.logInfo(s"received a called traced service call with the id $id")
          result <- ExternalApiService
            .callApi(id)
          content <- result.body.asString
          headersWithTracing <- B3HTTPResponseTracing.appendHeadersToResponse(
            Headers.empty
          )
        } yield {
          Response.text(content).copy(headers = headersWithTracing)
        }
      }
  }
}
