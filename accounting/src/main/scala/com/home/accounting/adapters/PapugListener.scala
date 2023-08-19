package com.home.accounting.adapters

import zio.*

trait PapugListener:
  def listenUpdates: UIO[Unit]

object KafkaPapugListener:
  val live: ULayer[KafkaPapugListener] = ZLayer.succeed(KafkaPapugListener())

final case class KafkaPapugListener() extends PapugListener:
  override def listenUpdates: UIO[Unit] = ZIO.logInfo("listening to papugs") *> ZIO.never
