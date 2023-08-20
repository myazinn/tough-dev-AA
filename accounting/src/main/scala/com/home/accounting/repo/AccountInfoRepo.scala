package com.home.accounting.repo

import com.home.accounting.model.*
import doobie.*
import doobie.implicits.*

import zio.interop.catz.*
import zio.{ Task as ZTask, * }

trait AccountInfoRepo:
  def upsert(accountInfo: AccountInfo): UIO[Unit]
  def get(papugId: PapugId): UIO[Option[AccountInfo]]

object DoobieAccountInfoRepo:
  val live =
    ZLayer.fromZIO:
      for
        transactor <- ZIO.service[Transactor[ZTask]]
        _ <- sql"""CREATE TABLE IF NOT EXISTS account_info (
                  |  id SERIAL PRIMARY KEY,
                  |  papug_id TEXT NOT NULL REFERENCES papugs (id) ON DELETE CASCADE,
                  |  balance BIGINT NOT NULL,
                  |  CONSTRAINT account_info_papug_id_unique UNIQUE (papug_id)
                  |)""".stripMargin.update.run.transact(transactor).unit.orDie
      yield DoobieAccountInfoRepo(transactor)

case class DoobieAccountInfoRepo(transactor: Transactor[ZTask]) extends AccountInfoRepo:
  override def upsert(accountInfo: AccountInfo): UIO[Unit] =
    sql"""INSERT INTO account_info (papug_id, balance)
         | VALUES (${accountInfo.papugId}, ${accountInfo.balance})
         | ON CONFLICT (papug_id) DO UPDATE SET
         |   balance = EXCLUDED.balance
          """.stripMargin.update.run.transact(transactor).unit.orDie

  override def get(papugId: PapugId): UIO[Option[AccountInfo]] =
    sql"""select papug_id, balance from account_info where papug_id = $papugId"""
      .query[AccountInfo]
      .option
      .transact(transactor)
      .orDie

  private given Get[PapugId] = Get[String].map(PapugId(_))
  private given Put[PapugId] = Put[String].contramap(PapugId.unwrap)

  private given Get[Money] = Get[Long].map(Money(_))
  private given Put[Money] = Put[Long].contramap(Money.unwrap)

end DoobieAccountInfoRepo
