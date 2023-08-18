package com.home.tasks.service

import com.home.tasks.model.*
import com.home.tasks.repo.PapugRepo

import zio.*
import zio.stream.UStream

trait PapugService:
  def upsert(papug: Papug): UIO[Unit]
  def findByEmail(email: Email): UIO[Option[Papug]]
  def findAll: UStream[Papug]

object PapugServiceLive:
  val live: URLayer[PapugRepo, PapugServiceLive] = ZLayer.fromFunction(PapugServiceLive.apply _)

final case class PapugServiceLive(papugRepo: PapugRepo) extends PapugService:
  override def upsert(papug: Papug): UIO[Unit]               = papugRepo.upsert(papug)
  override def findByEmail(email: Email): UIO[Option[Papug]] = papugRepo.findByEmail(email)
  override def findAll: UStream[Papug]                       = papugRepo.findAll
