package com.home.accounting.service

import com.home.accounting.model.{ Task, TaskId }
import com.home.accounting.repo.TaskRepo

import zio.*

trait TaskService:
  def upsert(task: Task): UIO[Unit]
  def find(id: TaskId): UIO[Option[Task]]

object TaskServiceLive:
  val live: URLayer[TaskRepo, TaskServiceLive] = ZLayer.fromFunction(TaskServiceLive.apply _)

case class TaskServiceLive(repo: TaskRepo) extends TaskService:
  override def upsert(task: Task): UIO[Unit]       = ZIO.logInfo(s"Upserting task $task") *> repo.upsert(task)
  override def find(id: TaskId): UIO[Option[Task]] = ZIO.logInfo(s"Searching for task $id") *> repo.find(id)
