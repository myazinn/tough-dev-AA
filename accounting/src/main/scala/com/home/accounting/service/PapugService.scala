package com.home.accounting.service

import com.home.accounting.model.*
import com.home.accounting.repo.PapugRepo

import zio.*

trait PapugService:
  def upsert(papug: Papug): UIO[Unit]

object PapugServiceLive:
  val live: URLayer[PapugRepo, PapugServiceLive] = ZLayer.fromFunction(PapugServiceLive.apply _)

final case class PapugServiceLive(papugRepo: PapugRepo) extends PapugService:

  override def upsert(papug: Papug): UIO[Unit] =
    papugRepo.upsert(papug) *> ZIO.logInfo(s"Upserted papug: $papug")

end PapugServiceLive
