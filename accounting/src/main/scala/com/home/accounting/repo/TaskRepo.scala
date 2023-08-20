package com.home.accounting.repo

import java.sql.Timestamp
import java.time.Instant

import com.home.accounting.model.Task.Status
import com.home.accounting.model.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*

import zio.interop.catz.*
import zio.{ Task as ZTask, * }

trait TaskRepo:
  def upsert(task: Task): UIO[Unit]
  def find(id: TaskId): UIO[Option[Task]]

object DoobieTaskRepo:
  val live: URLayer[Transactor[ZTask], DoobieTaskRepo] =
    ZLayer.fromZIO:
      for
        transactor <- ZIO.service[Transactor[ZTask]]
        _ <-
          sql"""CREATE TABLE IF NOT EXISTS tasks (
               |  id                   SERIAL PRIMARY KEY,
               |  public_id            TEXT NOT NULL,
               |  worker_id            TEXT NOT NULL,
               |  author_id            TEXT NOT NULL,
               |  pay_to_worker        BIGINT NOT NULL,
               |  withdraw_from_author BIGINT NOT NULL,
               |  status               TEXT NOT NULL,
               |  title                TEXT NOT NULL,
               |  description          TEXT,
               |  updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
               |  CONSTRAINT public_id_unique UNIQUE (public_id)
               |)
               |""".stripMargin.update.run
            .transact(transactor)
            .unit
            .orDie
      yield DoobieTaskRepo(transactor)

final case class DoobieTaskRepo(transactor: Transactor[ZTask]) extends TaskRepo:
  override def upsert(t: Task): UIO[Unit] =
    sql"""INSERT INTO tasks (public_id, worker_id, author_id, pay_to_worker, withdraw_from_author, status, title, description, updated_at)
         | VALUES (${t.publicId}, ${t.workerId}, ${t.authorId}, ${t.payToWorker}, ${t.withdrawFromAuthor}, ${t.status}, ${t.title}, ${t.description}, ${t.updatedAt})
         | ON CONFLICT (public_id)
         |  DO UPDATE SET
         |    worker_id = EXCLUDED.worker_id,
         |    author_id = EXCLUDED.author_id,
         |    pay_to_worker = EXCLUDED.pay_to_worker,
         |    withdraw_from_author = EXCLUDED.withdraw_from_author,
         |    status = EXCLUDED.status,
         |    title = EXCLUDED.title,
         |    description = EXCLUDED.description,
         |    updated_at = EXCLUDED.updated_at
         |""".stripMargin.update.run.transact(transactor).orDie.unit
  end upsert

  override def find(id: TaskId): UIO[Option[Task]] =
    sql"""SELECT public_id, worker_id, author_id, pay_to_worker, withdraw_from_author, status, title, description, updated_at
         | FROM tasks
         | WHERE public_id = $id""".stripMargin
      .query[Task]
      .option
      .transact(transactor)
      .orDie

  private given Get[TaskId] = Get[String].map(TaskId(_))
  private given Put[TaskId] = Put[String].contramap(TaskId.unwrap)

  private given Get[PapugId] = Get[String].map(PapugId(_))
  private given Put[PapugId] = Put[String].contramap(PapugId.unwrap)

  private given Get[Money] = Get[Long].map(Money(_))
  private given Put[Money] = Put[Long].contramap(Money.unwrap)

  private given Get[Task.Status] = Get[String].map(Task.Status.valueOf)
  private given Put[Task.Status] = Put[String].contramap(_.toString)

  private given Get[Instant] = Get[Timestamp].map(_.toInstant)
  private given Put[Instant] = Put[Timestamp].contramap(Timestamp.from)

end DoobieTaskRepo
