package com.home.tasks.service

import com.home.tasks.model.Task

import zio.{ Chunk, UIO, ULayer, ZIO, ZLayer }

trait TaskPublisher:
  def publish(tasks: Chunk[Task]): UIO[Unit]

object TaskPublisherLive:
  val live: ULayer[TaskPublisherLive] = ZLayer.succeed(TaskPublisherLive())

final case class TaskPublisherLive() extends TaskPublisher:
  override def publish(tasks: Chunk[Task]): UIO[Unit] =
    ZIO.logInfo(s"Publishing tasks: $tasks")
