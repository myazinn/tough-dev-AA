package com.home.tasks.service

import com.home.tasks.model.TaskUpdate

import zio.{ Chunk, UIO }

trait TaskPublisher:
  def publish(tasks: Chunk[TaskUpdate]): UIO[Unit]
