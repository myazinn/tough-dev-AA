package com.home.tasks.repo

import com.home.tasks.model.*
import doobie.*
import doobie.implicits.*

import zio.interop.catz.*
import zio.stream.UStream
import zio.stream.interop.fs2z.*
import zio.{ Task as ZTask, * }

trait PapugRepo:
  def upsert(papug: Papug): UIO[Unit]
  def findByEmail(email: Email): UIO[Option[Papug]]
  def findAll: UStream[Papug]

object DoobiePapugRepo:
  val live: URLayer[Transactor[ZTask], DoobiePapugRepo] =
    ZLayer.fromZIO:
      for
        transactor <- ZIO.service[Transactor[ZTask]]
        _ <-
          sql"""CREATE TABLE IF NOT EXISTS papugs (
               |  id    TEXT PRIMARY KEY,
               |  email TEXT NOT NULL,
               |  roles TEXT NOT NULL,
               |  CONSTRAINT email_unique UNIQUE (email)
               |)
               |""".stripMargin.update.run
            .transact(transactor)
            .unit
            .orDie
      yield DoobiePapugRepo(transactor)

final case class DoobiePapugRepo(transactor: Transactor[ZTask]) extends PapugRepo:
  override def upsert(papug: Papug): UIO[Unit] =
    sql"""INSERT INTO papugs (id, email, roles)
         | VALUES (${papug.id}, ${papug.email}, ${papug.roles})
         | ON CONFLICT (id)
         |  DO UPDATE SET
         |    email = ${papug.email},
         |    roles = ${papug.roles}
         |""".stripMargin.update.run
      .transact(transactor)
      .unit
      .orDie

  override def findByEmail(email: Email): UIO[Option[Papug]] =
    sql"SELECT id, email, roles FROM papugs WHERE email = ${Email.unwrap(email)}"
      .query[Papug]
      .option
      .transact(transactor)
      .orDie

  override def findAll: UStream[Papug] =
    sql"SELECT id, email, roles FROM papugs"
      .query[Papug]
      .stream
      .transact(transactor)
      .toZStream()
      .orDie

  private given Get[Email] = Get[String].map(Email(_))
  private given Put[Email] = Put[String].contramap(Email.unwrap)

  private given Get[PapugId] = Get[String].map(PapugId(_))
  private given Put[PapugId] = Put[String].contramap(PapugId.unwrap)

  private given Get[Set[Role]] = Get[String].map(_.split(",").filter(_.nonEmpty).map(Role(_)).toSet)
  private given Put[Set[Role]] = Put[String].contramap(_.map(Role.unwrap).mkString(","))

end DoobiePapugRepo
