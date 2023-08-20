package com.home.accounting.service

import java.util.UUID

import com.home.accounting.model.TaskUpdate.Update
import com.home.accounting.model.*
import com.home.accounting.repo.*

import zio.{ Task as _, * }

trait AccountingService:
  def handleTaskUpdate(update: TaskUpdate): UIO[Unit]

object AccountingServiceLive:
  val live: URLayer[
    TaskService & InvoicePublisher & AccountInfoPublisher & AccountInfoRepo & InvoiceRepo,
    AccountingServiceLive
  ] =
    ZLayer.fromFunction(AccountingServiceLive.apply _)

case class AccountingServiceLive(
  taskService: TaskService,
  invoicePublisher: InvoicePublisher,
  accountInfoPublisher: AccountInfoPublisher,
  accountInfoRepo: AccountInfoRepo,
  invoiceRepo: InvoiceRepo
) extends AccountingService:

  override def handleTaskUpdate(update: TaskUpdate): UIO[Unit] =
    update.update match
      case Update.Created(workerId, authorId, title, description) =>
        for
          toPay      <- Random.nextIntBetween(20, 40)
          toWithdraw <- Random.nextIntBetween(-20, -10)
          task = Task(
            publicId = update.publicId,
            workerId = workerId,
            authorId = authorId,
            payToWorker = Money(toPay),
            withdrawFromAuthor = Money(toWithdraw),
            status = Task.Status.InProgress,
            title = title,
            description = description,
            updatedAt = update.updatedAt
          )

          _ <- addPendingTask(task) *> updateAuthorAccount(task) *> issueAuthorInvoice(task)
        yield ()
      case Update.Reassigned(workerId) =>
        taskService
          .find(update.publicId)
          .flatMap:
            case Some(task) =>
              taskService.upsert(task.copy(workerId = workerId, updatedAt = update.updatedAt))
            case None =>
              ZIO.logWarning("That's weird. Couldn't find task to reassign.") // todo send to dead letter queue
      case Update.Completed =>
        taskService
          .find(update.publicId)
          .flatMap:
            case Some(task) =>
              taskService.upsert(task.copy(status = Task.Status.Done, updatedAt = update.updatedAt))
            case None =>
              ZIO.logWarning("That's weird. Couldn't find task to complete.") // todo send to dead letter queue
  end handleTaskUpdate

  private def addPendingTask(task: Task): UIO[Unit] = taskService.upsert(task)

  private def updateAuthorAccount(task: Task): UIO[Unit] =
    accountInfoRepo
      .get(task.authorId)
      .map(_.getOrElse(AccountInfo(task.authorId, Money(0))))
      .flatMap: account =>
        val updated = account.copy(balance = account.balance - task.withdrawFromAuthor)
        accountInfoRepo.upsert(updated) *> accountInfoPublisher.publish(updated)

  private def issueAuthorInvoice(task: Task): UIO[Unit] =
    val invoice =
      Invoice(
        InvoiceId(UUID.randomUUID().toString),
        task.authorId,
        task.publicId,
        task.withdrawFromAuthor,
        Invoice.Operation.Expense
      )
    invoiceRepo.insert(invoice) *> invoicePublisher.publish(invoice)

end AccountingServiceLive
