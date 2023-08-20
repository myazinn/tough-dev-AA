package com.home.accounting.service

import com.home.accounting.model.AccountInfo

import zio.*

trait AccountInfoPublisher:
  def publish(accountInfo: AccountInfo): UIO[Unit]
