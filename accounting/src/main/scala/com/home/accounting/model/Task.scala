package com.home.accounting.model

import java.time.Instant

case class Task(
  publicId: TaskId,
  workerId: PapugId,
  authorId: PapugId,
  payToWorker: Money,
  withdrawFromAuthor: Money,
  status: Task.Status,
  title: String,
  description: Option[String],
  updatedAt: Instant
)

object Task:
  enum Status:
    case InProgress, Done
