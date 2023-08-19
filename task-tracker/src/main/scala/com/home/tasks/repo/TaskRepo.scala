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
               |  papug_id    TEXT NOT NULL,
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
    // todo use batch insert
    def upsertOne(task: Task): ZTask[Any] =
      sql"""INSERT INTO tasks (public_id, papug_id, title, description, status, updated_at)
           | VALUES (${task.publicId}, ${task.papugId}, ${task.title}, ${task.description}, ${task.status}, ${task.updatedAt})
           | ON CONFLICT (public_id)
           |  DO UPDATE SET
           |    papug_id = ${task.papugId},
           |    title = ${task.title},
           |    description = ${task.description},
           |    status = ${task.status},
           |    updated_at = ${task.updatedAt}
           |""".stripMargin.update.run
        .transact(transactor)

    ZIO.foreachParDiscard(tasks)(upsertOne).unit.orDie
  end upsert

  override def findById(id: TaskId): UIO[Option[Task]] =
    sql"""SELECT public_id, papug_id, title, description, status, updated_at
         | FROM tasks
         | WHERE public_id = ${TaskId.unwrap(id)}""".stripMargin
      .query[Task]
      .option
      .transact(transactor)
      .orDie

  override def findAllForPapug(papug: PapugId): UStream[Task] =
    sql"""SELECT public_id, papug_id, title, description, status, updated_at
         | FROM tasks
         | WHERE papug_id = ${PapugId.unwrap(papug)}""".stripMargin
      .query[Task]
      .stream
      .transact(transactor)
      .toZStream()
      .orDie

  override def findAllWithStatus(status: Status): UStream[Task] =
    sql"""SELECT public_id, papug_id, title, description, status, updated_at
         | FROM tasks
         | WHERE status = ${status.toString}""".stripMargin
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
