package com.home.accounting.service

import com.home.accounting.model.Invoice

import zio.*

trait InvoicePublisher:
  def publish(invoice: Invoice): UIO[Unit]
