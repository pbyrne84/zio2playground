package com.github.pbyrne84.zio2playground.routes

import com.github.pbyrne84.zio2playground.BaseSpec
import com.github.pbyrne84.zio2playground.db.PersonRepo
import com.github.pbyrne84.zio2playground.testbootstrap.AllTestBootstrap
import com.github.pbyrne84.zio2playground.testbootstrap.extensions.ClientOps
import com.github.pbyrne84.zio2playground.testbootstrap.wiremock.ServerAWireMock
import org.mockito.Mockito
import zhttp.http.{HttpData, Request, URL}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.test.TestAspect.{sequential, success}
import zio.test._
import zio.{Clock, Random, Scope, ZIO, ZLayer}

object RoutesSpec extends BaseSpec with ClientOps {
  override def spec = suite("routes")(
    suite("delete3")(
      test("should use mock for repo") {
        val request = Request(url = URL.empty.setPath("/delete3"))
        val personRepoMock = Mockito.mock(classOf[PersonRepo])

        val clockLayer = ZLayer.succeed(Mockito.mock(classOf[Clock]))
        val randomLayer = ZLayer.succeed(Mockito.mock(classOf[Random]))
        val personLayer = ZLayer.succeed(personRepoMock)

        val deleteCount = 1000L
        Mockito
          .when(personRepoMock.deletePeople())
          .thenReturn(ZIO.succeed(deleteCount))

        (for {
          _ <- reset
          result <- ZIO.serviceWithZIO[Routes](_.routes.apply(request))
          _ = println(result.data)
        } yield assertTrue(result.data == HttpData.fromString(deleteCount.toString)))
          .provideSome[BaseSpec.Shared](
            Routes.routesLayer,
            clockLayer,
            randomLayer,
            personLayer,
            Scope.default
          )

      }
    ),
    suite("client test")(
      test("should get response from wiremock") {
        val expected = "empty calories are the best"
        for {
          _ <- reset
          params <- AllTestBootstrap.getParams
          _ <- ServerAWireMock.stubCall(expected)
          _ <- ServerAWireMock.getStubbings
          url = s"http://localhost:${params.serverAPort}/banana"
          _ = println(url)
          response <- Client.request(url)
          dataAsString <- response.dataAsString
          _ <- ServerAWireMock.getStubbings
        } yield (assertTrue(
          dataAsString == expected
        ))

      }.provideSome[BaseSpec.Shared](EventLoopGroup.auto(), ChannelFactory.auto)
    )
  ) @@ sequential @@ success
}
