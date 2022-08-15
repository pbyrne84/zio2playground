package com.github.pbyrne84.zio2playground

import com.github.pbyrne84.zio2playground.Builds.PersonServiceBuild
import com.github.pbyrne84.zio2playground.Main.Person
import com.github.pbyrne84.zio2playground.db.PersonRepo
import com.github.pbyrne84.zio2playground.service.PersonService
import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill
import zio.{ZIO, ZIOAppDefault, ZLayer}

import java.sql.SQLException
import javax.sql.DataSource
object Main {
  import io.getquill._

  val ctx = new PostgresJdbcContext(SnakeCase, "testPostgresDB")

  case class Person(id: Int, firstName: String, lastName: String, age: Int)

  def main(args: Array[String]): Unit = {
    Math.sqrt(1234)

    upgradeDb()
    run()
  }

  private def upgradeDb(): Unit = {
    import org.flywaydb.core.Flyway
    val user = "postgres"
    val password = "password"
    val url = "jdbc:postgresql://localhost/testdb"
    val flyway = Flyway.configure
      .dataSource(url, user, password)
      .load

    flyway.migrate()
  }

  private def run(): Unit = {

    ZioMain.main(Array.empty)

  }

  private def a: Either[RuntimeException, Int] = {
    Right(1)
  }

}

trait QuillDbConfig {
  val quillLayer: ZLayer[DataSource, Nothing, Quill.Postgres[SnakeCase.type]] =
    Quill.Postgres
      .fromNamingStrategy(SnakeCase)
      .asInstanceOf[ZLayer[DataSource, Nothing, Quill.Postgres[SnakeCase.type]]]

  val dsLayer: ZLayer[Any, Throwable, DataSource] = Quill.DataSource.fromPrefix("testPostgresDB")
}

object ZioMain extends ZIOAppDefault with QuillDbConfig {

  override def run = {
//    DataService
//      .getZioServiceWithZio(_.getPeople)
//      .provide(quillLayer, dsLayer, DataServiceLive.layer)
//      .debug("Results")
//      .exitCode

    println(PersonRepo.layer.getClass)

//    ZLayer
//      .make[PersonRepo](PersonRepo.instance, quillLayer, dsLayer)
//      .build
//      .map(_.get[PersonRepo])
//      .flatMap(program)

    run2
  }

  def run2 = {
    val personRepoBuild: ZLayer[Any, Throwable, PersonRepo] = Builds.PersonRepoBuild.personRepoMake

    PersonServiceBuild.personServiceBuild
      .flatMap(program)
  }

  private def program(dataService: PersonService): ZIO[Any, SQLException, Unit] = {
    for {
      // _ <- dataService.deletePeople()
      _ <- dataService.addPerson(Person(2, "a", "b", 3))
    } yield ()

  }

}
