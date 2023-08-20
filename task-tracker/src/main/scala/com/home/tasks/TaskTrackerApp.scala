package com.home.tasks

import com.home.avro.schema.RedpandaAvroSchemaRegistry
import com.home.tasks.adapters.*
import com.home.tasks.repo.*
import com.home.tasks.service.*
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor

import zio.*
import zio.http.{ Client, Server }
import zio.interop.catz.*
import zio.kafka.consumer.*

object TaskTrackerApp extends ZIOAppDefault:

  private val brokers        = List("redpanda:9092")
  private val schemaRegistry = "http://redpanda:18081"

  private val usersStreamingTopic = "users-streaming"

  private val tasksCreatedSubject    = "tasks-lifecycle-created-value"
  private val tasksReassignedSubject = "tasks-lifecycle-reassigned-value"
  private val tasksCompletedSubject  = "tasks-lifecycle-completed-value"
  private val usersStreamingSubject  = "users-streaming-value"

  override def run: ZIO[Scope, Any, Any] =
    val consumeMessages = ZIO.serviceWithZIO[PapugListener](_.listenUpdates)

    val runHTTPServer =
      for
        routes <- ZIO.serviceWith[TaskServiceRoutes](_.routes)
        _      <- Server.serve(routes.toHttpApp)
      yield ()

    val app = runHTTPServer.race(consumeMessages)

    val consumerSettings = ZLayer.succeed(ConsumerSettings(brokers).withGroupId("task-tracker"))
    val transactor =
      ZLayer.scoped {
        val config = new HikariConfig()
        config.setDriverClassName("org.postgresql.Driver")
        config.setJdbcUrl("jdbc:postgresql://postgres:5432/tasktracker")
        config.setUsername("tasktracker")
        config.setPassword("tasktracker")
        config.setMaximumPoolSize(32)
        HikariTransactor.fromHikariConfig[Task](config).toScopedZIO
      }

    app.provideSome[Scope](
      TaskServiceRoutesLive.live,
      KafkaPapugListener.live,
      TaskServiceLive.live,
      PapugServiceLive.live,
      KafkaTaskPublisher.live,
      Server.defaultWithPort(8000),
      Client.default,
      RedpandaAvroSchemaRegistry.live,
      ZLayer.succeed(RedpandaAvroSchemaRegistry.Config(schemaRegistry)),
      ZLayer.succeed(KafkaTaskPublisher.Config(tasksCreatedSubject, tasksReassignedSubject, tasksCompletedSubject)),
      ZLayer.succeed(KafkaPapugListener.Config(usersStreamingTopic, usersStreamingSubject)),
      DoobieTaskRepo.live,
      DoobiePapugRepo.live,
      transactor,
      consumerSettings
    )
