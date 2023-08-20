package com.home.accounting.repo

import com.home.accounting.model.*
import doobie.*
import doobie.implicits.*

import zio.interop.catz.*
import zio.{ Task as ZTask, * }

trait PapugRepo:
  def upsert(papug: Papug): UIO[Unit]

object DoobiePapugRepo:
  val live: URLayer[Transactor[ZTask], DoobiePapugRepo] =
    ZLayer.fromZIO:
      for
        transactor <- ZIO.service[Transactor[ZTask]]
        _ <-
          sql"""CREATE TABLE IF NOT EXISTS papugs (
               |  id TEXT PRIMARY KEY
               |)
               |""".stripMargin.update.run
            .transact(transactor)
            .unit
            .orDie
      yield DoobiePapugRepo(transactor)

final case class DoobiePapugRepo(transactor: Transactor[ZTask]) extends PapugRepo:
  override def upsert(papug: Papug): UIO[Unit] =
    sql"""INSERT INTO papugs (id)
         | VALUES (${papug.id})
         | ON CONFLICT DO NOTHING
         |""".stripMargin.update.run
      .transact(transactor)
      .unit
      .orDie

  private given Put[PapugId] = Put[String].contramap(PapugId.unwrap)
end DoobiePapugRepo
