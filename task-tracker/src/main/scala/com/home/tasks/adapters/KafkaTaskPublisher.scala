package com.home.tasks.adapters

import com.home.tasks.model.TaskUpdate
import com.home.tasks.service.TaskPublisher

import zio.{ Chunk, UIO, ULayer, ZIO, ZLayer }

object KafkaTaskPublisher:
  val live: ULayer[KafkaTaskPublisher] = ZLayer.succeed(KafkaTaskPublisher())

final case class KafkaTaskPublisher() extends TaskPublisher:
  override def publish(tasks: Chunk[TaskUpdate]): UIO[Unit] =
    ZIO.logInfo(s"Publishing tasks: $tasks")
