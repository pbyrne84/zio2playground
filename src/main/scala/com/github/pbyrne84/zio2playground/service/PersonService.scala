package com.github.pbyrne84.zio2playground.service

import com.github.pbyrne84.zio2playground.db.{Person, PersonRepo}
import zio.{ZIO, ZLayer}

import java.sql.SQLException

object PersonService {
  def instance: ZLayer[PersonRepo, Nothing, PersonService] = ZLayer {
    for {
      personRepo <- ZIO.service[PersonRepo]

    } yield new PersonService(personRepo)
  }

}

class PersonService(personRepo: PersonRepo) {

  def addPerson(person: Person): ZIO[Any, SQLException, Long] =
    personRepo.addPerson(person)

  def deletePeople(): ZIO[Any, SQLException, Long] =
    personRepo.deletePeople()

}
