package com.home.tasks.repo

import java.sql.Timestamp
import java.time.Instant

import com.home.tasks.model.Task.Status
import com.home.tasks.model.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*

import zio.interop.catz.*
import zio.stream.UStream
import zio.stream.interop.fs2z.*
import zio.{ Task as ZTask, * }

trait TaskRepo:
  def upsert(tasks: Chunk[Task]): UIO[Unit]
  def findById(id: TaskId): UIO[Option[Task]]

  def findAllForPapug(papug: PapugId): UStream[Task]
  def findAllWithStatus(status: Status): UStream[Task]

object DoobieTaskRepo:
  val live: URLayer[Transactor[ZTask], DoobieTaskRepo] =
    ZLayer.fromZIO:
      for
        transactor <- ZIO.service[Transactor[ZTask]]
        _ <-
          sql"""CREATE TABLE IF NOT EXISTS tasks (
               |  id          SERIAL PRIMARY KEY,
               |  public_id   TEXT NOT NULL,
               |  worker_id    TEXT NOT NULL,
               |  author_id    TEXT NOT NULL,
               |  title       TEXT NOT NULL,
               |  description TEXT,
               |  status      TEXT NOT NULL,
               |  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
               |  CONSTRAINT public_id_unique UNIQUE (public_id)
               |)
               |""".stripMargin.update.run
            .transact(transactor)
            .unit
            .orDie
      yield DoobieTaskRepo(transactor)

final case class DoobieTaskRepo(transactor: Transactor[ZTask]) extends TaskRepo:
  override def upsert(tasks: Chunk[Task]): UIO[Unit] =
    val sql =
      """INSERT INTO tasks (public_id, worker_id, author_id, title, description, status, updated_at)
        | VALUES (?, ?, ?, ?, ?, ?, ?)
        | ON CONFLICT (public_id)
        |  DO UPDATE SET
        |    worker_id = EXCLUDED.worker_id,
        |    author_id = EXCLUDED.author_id,
        |    title = EXCLUDED.title,
        |    description = EXCLUDED.description,
        |    status = EXCLUDED.status,
        |    updated_at = EXCLUDED.updated_at
        |""".stripMargin

    Update[Task](sql).updateMany(tasks).transact(transactor).orDie.unit
  end upsert

  override def findById(id: TaskId): UIO[Option[Task]] =
    sql"""SELECT public_id, worker_id, author_id, title, description, status, updated_at
         | FROM tasks
         | WHERE public_id = $id""".stripMargin
      .query[Task]
      .option
      .transact(transactor)
      .orDie

  override def findAllForPapug(papug: PapugId): UStream[Task] =
    sql"""SELECT public_id, worker_id, author_id, title, description, status, updated_at
         | FROM tasks
         | WHERE worker_id = $papug OR author_id = $papug""".stripMargin
      .query[Task]
      .stream
      .transact(transactor)
      .toZStream()
      .orDie

  override def findAllWithStatus(status: Status): UStream[Task] =
    sql"""SELECT public_id, worker_id, author_id, title, description, status, updated_at
         | FROM tasks
         | WHERE status = $status""".stripMargin
      .query[Task]
      .stream
      .transact(transactor)
      .toZStream()
      .orDie

  private given Get[TaskId] = Get[String].map(TaskId(_))
  private given Put[TaskId] = Put[String].contramap(TaskId.unwrap)

  private given Get[PapugId] = Get[String].map(PapugId(_))
  private given Put[PapugId] = Put[String].contramap(PapugId.unwrap)

  private given Get[Task.Status] = Get[String].map(Task.Status.valueOf)
  private given Put[Task.Status] = Put[String].contramap(_.toString)

  private given Get[Instant] = Get[Timestamp].map(_.toInstant)
  private given Put[Instant] = Put[Timestamp].contramap(Timestamp.from)

end DoobieTaskRepo
