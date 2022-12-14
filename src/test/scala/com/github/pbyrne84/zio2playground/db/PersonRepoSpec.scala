package com.github.pbyrne84.zio2playground.db
import com.github.pbyrne84.zio2playground.Builds.PersonServiceBuild
import com.github.pbyrne84.zio2playground.{BaseSpec, Builds}
import org.mockito.Mockito
import zio.test.TestAspect.sequential
import zio.test._
import zio.{Scope, ZIO, ZLayer}

object PersonRepoSpec extends BaseSpec with QuillDbConfig {

  def spec = suite("Person Repo")(
    test("adds a person via the service") {
      for {
        _ <- reset
        personService <- PersonServiceBuild.personServiceBuild
        _ <- personService.deletePeople()
        count <- personService.addPerson(Person(43, "a", "b", 400))
      } yield assertTrue(count == 1L)
    },
    test("adds a person via the repo") {
      for {
        _ <- reset
        personRepo <- Builds.PersonRepoBuild.personRepoBuild
        _ <- personRepo.deletePeople()
        count <- personRepo.addPerson(Person(43, "a", "b", 400))
      } yield assertTrue(count == 1L)
    },
    test("adds a person to the repo using provided layers") {
      for {
        _ <- reset
        _ <- PersonRepo.deletePeople()
        count <- PersonRepo.addPerson(Person(43, "a", "b", 400))
      } yield assertTrue(count == 1L)
    }.provideSome[BaseSpec.Shared](Builds.PersonRepoBuild.personRepoMake)
    /*
      Builds.PersonRepoBuild.personRepoMake hides the quill connectivity as things like that can vary per instance across the project.
      The assumption there is only 1 instance of each highly abstract type could probably get you into trouble
      so it is good to know how to escape that problem and add some compartmentalising to your layers.

      Also the the macros rely on the operations not being local so the ZLayer.make is separate from the PersonRepo.scala
      file

      lazy val personRepoMake: ZLayer[Any, Throwable, PersonRepo] = ZLayer.make[PersonRepo](PersonRepo.layer, quillLayer, dsLayer)
     */

    ,
    test("uses mocking") {
      val mockPersonRepo = Mockito.mock(classOf[PersonRepo])
      val mockPersonRepoLayer = ZLayer.succeed(mockPersonRepo)

      Mockito
        .when(
          mockPersonRepo
            .deletePeople()
        )
        .thenReturn(ZIO.succeed(1L))

      val person = Person(43, "a", "b", 400)
      Mockito
        .when(
          mockPersonRepo
            .addPerson(person)
        )
        .thenReturn(ZIO.succeed(1L))

      for {
        _ <- reset
        personRepo <- Builds.PersonServiceBuild.buildWithInjections(mockPersonRepoLayer)
        _ <- personRepo.deletePeople()
        count <- personRepo.addPerson(person)
      } yield assertTrue(count == 1L)
    }
  ).provideSomeShared[BaseSpec.Shared](
    Scope.default
  ) @@ sequential

}
