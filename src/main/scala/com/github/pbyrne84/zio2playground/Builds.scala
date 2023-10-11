package com.github.pbyrne84.zio2playground

import com.github.pbyrne84.zio2playground.db.{PersonRepo, QuillDbConfig}
import com.github.pbyrne84.zio2playground.routes.Routes
import com.github.pbyrne84.zio2playground.service.PersonService
import zio.{Scope, ZIO, ZLayer}

//This stuff needs to be separate due to macros
object Builds {

  object PersonRepoBuild extends QuillDbConfig {

    lazy val personRepoMake: ZLayer[Any, Throwable, PersonRepo] = ZLayer
      .make[PersonRepo](PersonRepo.layer, quillLayer, dsLayer)

    lazy val personRepoBuild: ZIO[Any with Scope, Throwable, PersonRepo] =
      personRepoMake.build.map(_.get[PersonRepo])

  }

  object PersonServiceBuild {

    lazy val personServiceBuild: ZIO[Any with Scope, Throwable, PersonService] = ZLayer
      .make[PersonService](PersonService.instance, PersonRepoBuild.personRepoMake)
      .build
      .map(_.get[PersonService])

    def buildWithInjections(
        personRepoLayer: ZLayer[Any, Throwable, PersonRepo]
    ): ZIO[Any with Scope, Throwable, PersonService] =
      ZLayer
        .make[PersonService](PersonService.instance, personRepoLayer)
        .build
        .map(_.get[PersonService])

  }

  object RoutesBuild {
    lazy val routesBuild: ZIO[Any with Scope, Throwable, Routes] = ZLayer
      .make[Routes](
        Routes.routesLayer,
        PersonRepoBuild.personRepoMake
      )
      .build
      .map(_.get[Routes])
  }

}
