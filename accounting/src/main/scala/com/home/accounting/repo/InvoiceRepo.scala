package com.home.accounting.repo

import com.home.accounting.model.*
import doobie.*
import doobie.implicits.*

import zio.interop.catz.*
import zio.stream.UStream
import zio.stream.interop.fs2z.*
import zio.{ Task as ZTask, * }

trait InvoiceRepo:
  def insert(invoice: Invoice): UIO[Unit]
  def getAll(papugId: PapugId): UStream[Invoice]

object DoobieInvoiceRepo:
  val live: URLayer[Transactor[ZTask], DoobieInvoiceRepo] =
    ZLayer.fromZIO:
      for
        transactor <- ZIO.service[Transactor[ZTask]]
        _ <-
          sql"""CREATE TABLE IF NOT EXISTS invoices (
               | id SERIAL PRIMARY KEY,
               | public_id TEXT NOT NULL,
               | papug_id TEXT NOT NULL REFERENCES papugs(id) ON DELETE CASCADE,
               | task_id TEXT NOT NULL REFERENCES tasks(public_id) ON DELETE CASCADE,
               | amount BIGINT NOT NULL,
               | operation TEXT NOT NULL,
               | CONSTRAINT invoices_public_id_unique UNIQUE (public_id)
               | )""".stripMargin.update.run.transact(transactor).orDie
      yield DoobieInvoiceRepo(transactor)
end DoobieInvoiceRepo

case class DoobieInvoiceRepo(transactor: Transactor[ZTask]) extends InvoiceRepo:
  override def insert(invoice: Invoice): UIO[Unit] =
    sql"""INSERT INTO invoices (public_id, papug_id, task_id, amount, operation)
         | VALUES (${invoice.publicId}, ${invoice.papugId}, ${invoice.taskId}, ${invoice.amount}, ${invoice.operation})""".stripMargin.update.run
      .transact(transactor)
      .unit
      .orDie

  override def getAll(papugId: PapugId): UStream[Invoice] =
    sql"""SELECT public_id, papug_id, task_id, amount, operation
         | FROM invoices
         | WHERE papug_id = $papugId""".stripMargin
      .query[Invoice]
      .stream
      .transact(transactor)
      .toZStream()
      .orDie

  private given Get[PapugId] = Get[String].map(PapugId(_))
  private given Put[PapugId] = Put[String].contramap(PapugId.unwrap)

  private given Get[InvoiceId] = Get[String].map(InvoiceId(_))
  private given Put[InvoiceId] = Put[String].contramap(InvoiceId.unwrap)

  private given Get[TaskId] = Get[String].map(TaskId(_))
  private given Put[TaskId] = Put[String].contramap(TaskId.unwrap)

  private given Get[Money] = Get[Long].map(Money(_))
  private given Put[Money] = Put[Long].contramap(Money.unwrap)

  private given Get[Invoice.Operation] = Get[String].map(Invoice.Operation.valueOf)
  private given Put[Invoice.Operation] = Put[String].contramap(_.toString)

end DoobieInvoiceRepo
