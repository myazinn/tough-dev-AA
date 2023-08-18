package com.home.tasks.repo

import java.sql.Timestamp

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
      val t = TaskInternal.fromTask(task)
      sql"""INSERT INTO tasks (public_id, papug_id, title, description, status, updated_at)
           | VALUES (${t.publicId}, ${t.papugId}, ${t.title}, ${t.description}, ${t.status}, ${t.updatedAt})
           | ON CONFLICT (public_id)
           |  DO UPDATE SET
           |    papug_id = ${t.papugId},
           |    title = ${t.title},
           |    description = ${t.description},
           |    status = ${t.status},
           |    updated_at = ${t.updatedAt}
           |""".stripMargin.update.run
        .transact(transactor)

    ZIO.foreachParDiscard(tasks)(upsertOne).unit.orDie
  end upsert

  override def findById(id: TaskId): UIO[Option[Task]] =
    sql"""SELECT public_id, papug_id, title, description, status, updated_at
         | FROM tasks
         | WHERE public_id = ${TaskId.unwrap(id)}""".stripMargin
      .query[TaskInternal]
      .map(TaskInternal.toTask)
      .option
      .transact(transactor)
      .orDie

  override def findAllForPapug(papug: PapugId): UStream[Task] =
    sql"""SELECT public_id, papug_id, title, description, status, updated_at
         | FROM tasks
         | WHERE papug_id = ${PapugId.unwrap(papug)}""".stripMargin
      .query[TaskInternal]
      .map(TaskInternal.toTask)
      .stream
      .transact(transactor)
      .toZStream()
      .orDie

  override def findAllWithStatus(status: Status): UStream[Task] =
    sql"""SELECT public_id, papug_id, title, description, status, updated_at
         | FROM tasks
         | WHERE status = ${status.toString}""".stripMargin
      .query[TaskInternal]
      .map(TaskInternal.toTask)
      .stream
      .transact(transactor)
      .toZStream()
      .orDie

  private case class TaskInternal(
    publicId: String,
    papugId: String,
    title: String,
    description: Option[String],
    status: String,
    updatedAt: Timestamp
  )
  private object TaskInternal:
    def fromTask(task: Task): TaskInternal =
      TaskInternal(
        publicId = TaskId.unwrap(task.publicId),
        papugId = PapugId.unwrap(task.papugId),
        title = task.title,
        description = task.description,
        status = task.status.toString,
        updatedAt = Timestamp.from(task.updatedAt)
      )
    def toTask(task: TaskInternal): Task =
      Task(
        publicId = TaskId(task.publicId),
        papugId = PapugId(task.papugId),
        title = task.title,
        description = task.description,
        status = Task.Status.valueOf(task.status),
        updatedAt = task.updatedAt.toInstant
      )
