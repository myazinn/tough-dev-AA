package com.home.accounting.adapters

import zio.*

trait TaskListener:
  def listenUpdates: UIO[Unit]

object KafkaTaskListener:
  val live: ULayer[KafkaTaskListener] = ZLayer.succeed(KafkaTaskListener())

final case class KafkaTaskListener() extends TaskListener:
  override def listenUpdates: UIO[Unit] = ZIO.logInfo("listening to tasks") *> ZIO.never
