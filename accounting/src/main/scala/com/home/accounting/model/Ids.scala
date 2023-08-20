package com.home.accounting.model

import zio.prelude.Newtype

object PapugId extends Newtype[String]
type PapugId = PapugId.Type

object TaskId extends Newtype[String]
type TaskId = TaskId.Type

object Email extends Newtype[String]
type Email = Email.Type

object Money extends Newtype[Long]
type Money = Money.Type
extension (money: Money)
  def +(other: Money): Money = Money(Money.unwrap(money) + Money.unwrap(other))
  def -(other: Money): Money = Money(Money.unwrap(money) - Money.unwrap(other))

object InvoiceId extends Newtype[String]
type InvoiceId = InvoiceId.Type

object Role extends Newtype[String]:
  val ADMIN: Role   = Role("ADMIN")
  val MANAGER: Role = Role("MANAGER")
type Role = Role.Type
