package com.home.accounting.model

import com.sksamuel.avro4s.AvroName

case class Invoice(
  @AvroName("public_id") publicId: InvoiceId,
  @AvroName("papug_id") papugId: PapugId,
  @AvroName("task_id") taskId: TaskId,
  @AvroName("amount") amount: Money,
  @AvroName("operation") operation: Invoice.Operation
)

object Invoice:
  enum Operation:
    case Income, Expense
