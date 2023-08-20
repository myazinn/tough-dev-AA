package com.home.tasks.model

import java.time.Instant

case class TaskUpdate(publicId: TaskId, update: TaskUpdate.Update, updatedAt: Instant)
object TaskUpdate:
  enum Update:
    case Created(
      workerId: PapugId,
      authorId: PapugId,
      title: String,
      description: Option[String]
    )
    case Reassigned(workerId: PapugId)
    case Completed
