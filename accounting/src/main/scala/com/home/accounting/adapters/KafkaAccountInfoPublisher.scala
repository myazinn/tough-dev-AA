package com.home.accounting.adapters

import com.home.accounting.model.AccountInfo
import com.home.accounting.service.AccountInfoPublisher

import zio.*

object KafkaAccountInfoPublisher:
  val live = ZLayer.succeed(KafkaAccountInfoPublisher())

case class KafkaAccountInfoPublisher() extends AccountInfoPublisher:
  override def publish(accountInfo: AccountInfo): UIO[Unit] = ZIO.logInfo(s"Publishing account info: $accountInfo")
