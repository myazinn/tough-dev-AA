package com.home.accounting.adapters

import com.home.accounting.model.Invoice
import com.home.accounting.service.InvoicePublisher

import zio.*

object KafkaInvoicePublisher:
  val live = ZLayer.succeed(KafkaInvoicePublisher())

case class KafkaInvoicePublisher() extends InvoicePublisher:
  def publish(invoice: Invoice): UIO[Unit] = ZIO.logInfo(s"Publishing invoice: $invoice")
