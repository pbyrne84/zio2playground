package com.github.pbyrne84.zio2playground.routes

import com.github.pbyrne84.zio2playground.Builds
import com.github.pbyrne84.zio2playground.Builds.RoutesBuild
import com.github.pbyrne84.zio2playground.db.PersonRepo
import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio._

import scala.util.Try

object Routes extends ZIOAppDefault {
  // Set a port
  private val PORT = 58479

  val routesLayer: ZLayer[PersonRepo with Random with Clock, Nothing, Routes] = ZLayer {
    for {
      clock <- ZIO.service[Clock]
      random <- ZIO.service[Random]
      personRepo <- ZIO.service[PersonRepo]
    } yield (new Routes(clock, random, personRepo))
  }

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
      .provide(ServerChannelFactory.auto, EventLoopGroup.auto(nThreads), Scope.default)
  }

  }

}

class Routes(clock: Clock, random: Random, personRepo: PersonRepo) {

  val routes: Http[Any with Scope, Throwable, Request, Response] = Http.collectZIO[Request] {
    case Method.GET -> !! / "random" => random.nextString(10).map(Response.text(_))
    case Method.GET -> !! / "utc" => clock.currentDateTime.map(s => Response.text(s.toString))
    case Method.GET -> !! / "banana" => clock.currentDateTime.map(s => Response.text(s.toString))
    case Method.GET -> !! / "delete1" =>
      Builds.PersonServiceBuild.personServiceBuild
        .flatMap(_.deletePeople().map((deleteCount: Long) => Response.text(deleteCount.toString)))

    case Method.GET -> !! / "delete2" =>
      PersonRepo
        .deletePeople()
        .map((deleteCount: Long) => Response.text(deleteCount.toString))
        .provideLayer(Builds.PersonRepoBuild.personRepoMake)

    case Method.GET -> !! / "delete3" =>
      personRepo.deletePeople().map((deleteCount: Long) => Response.text(deleteCount.toString))

  }

}
