package com.home.tasks.model

import java.time.Instant

case class Task(
  publicId: TaskId,
  workerId: PapugId,
  authorId: PapugId,
  title: String,
  description: Option[String],
  status: Task.Status,
  updatedAt: Instant
)

object Task:
  enum Status:
    case InProgress, Done

case class CreateTaskRequest(title: String, description: Option[String])

case class CreateTaskResponse(id: TaskId, workerId: PapugId)
