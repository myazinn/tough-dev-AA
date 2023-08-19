package com.home.tasks.service

import com.home.tasks.model.Task

import zio.{ Chunk, UIO }

trait TaskPublisher:
  def publish(tasks: Chunk[Task]): UIO[Unit]
