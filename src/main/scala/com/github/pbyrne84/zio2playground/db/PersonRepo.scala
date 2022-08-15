package com.github.pbyrne84.zio2playground.db

import com.github.pbyrne84.zio2playground.Main.Person
import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill
import zio.{ZIO, ZLayer}

import java.sql.SQLException

object PersonRepo {

  def layer: ZLayer[Quill.Postgres[SnakeCase], Nothing, PersonRepo] = ZLayer {
    for {
      quill <- ZIO.service[Quill.Postgres[SnakeCase]]
    } yield new PersonRepo(quill)
  }

  def deletePeople(): ZIO[PersonRepo, SQLException, Long] =
    ZIO.serviceWithZIO[PersonRepo](_.deletePeople())

  def addPerson(person: Person): ZIO[PersonRepo, SQLException, Long] =
    ZIO.serviceWithZIO[PersonRepo](_.addPerson(person))

}

case class PersonRepo(quill: Quill.Postgres[SnakeCase]) {
  import quill._

  def deletePeople(): ZIO[Any, SQLException, Long] =
    run(query[Person].delete)

  def getPeople: ZIO[Any, SQLException, List[Person]] =
    run(query[Person])

  def getPeopleOlderThan(age: Int): ZIO[Any, SQLException, List[Person]] =
    run(query[Person].filter(p => p.age > lift(age)))

  def addPerson(person: Person): ZIO[Any, SQLException, Long] =
    run(query[Person].insertValue(lift(person)))
}
