package com.home.accounting.model

import com.sksamuel.avro4s.AvroName

case class AccountInfo(
  @AvroName("papug_id") papugId: PapugId,
  @AvroName("balance") balance: Money
)
