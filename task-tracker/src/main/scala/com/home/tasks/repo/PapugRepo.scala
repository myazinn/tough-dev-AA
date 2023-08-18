package com.home.tasks.repo

import com.home.tasks.model.*
import doobie.Transactor
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
    val p = PapugInternal.fromPapug(papug)
    sql"""INSERT INTO papugs (id, email, roles)
         | VALUES (${p.id}, ${p.email}, ${p.roles})
         | ON CONFLICT (id)
         |  DO UPDATE SET
         |    email = ${p.email},
         |    roles = ${p.roles}
         |""".stripMargin.update.run
      .transact(transactor)
      .unit
      .orDie

  override def findByEmail(email: Email): UIO[Option[Papug]] =
    sql"SELECT id, email, roles FROM papugs WHERE email = ${Email.unwrap(email)}"
      .query[PapugInternal]
      .map(PapugInternal.toPapug)
      .option
      .transact(transactor)
      .orDie

  override def findAll: UStream[Papug] =
    sql"SELECT id, email, roles FROM papugs"
      .query[PapugInternal]
      .map(PapugInternal.toPapug)
      .stream
      .transact(transactor)
      .toZStream()
      .orDie

  private case class PapugInternal(id: String, email: String, roles: String)
  private object PapugInternal:
    def toPapug(p: PapugInternal): Papug =
      Papug(
        id = PapugId(p.id),
        email = Email(p.email),
        roles = p.roles.split(",").filter(_.nonEmpty).map(Role(_)).toSet
      )
    def fromPapug(p: Papug): PapugInternal =
      PapugInternal(
        id = PapugId.unwrap(p.id),
        email = Email.unwrap(p.email),
        roles = p.roles.map(Role.unwrap).mkString(",")
      )
