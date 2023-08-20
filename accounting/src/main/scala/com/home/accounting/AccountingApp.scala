package com.home.accounting

import com.home.accounting.adapters.*
import com.home.accounting.repo.*
import com.home.accounting.service.*
import com.home.avro.schema.RedpandaAvroSchemaRegistry
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor

import zio.*
import zio.http.*
import zio.interop.catz.*
import zio.kafka.consumer.ConsumerSettings

object AccountingApp extends ZIOAppDefault:

  private val brokers        = List("redpanda:9092")
  private val schemaRegistry = "http://redpanda:18081"

  private val tasksLifecycleTopic = "tasks-lifecycle"
  private val usersStreamingTopic = "users-streaming"

  private val tasksCreatedSubject         = "tasks-lifecycle-created-value"
  private val tasksReassignedSubject      = "tasks-lifecycle-reassigned-value"
  private val tasksCompletedSubject       = "tasks-lifecycle-completed-value"
  private val tasksLifecycleSubjectHeader = "tasks-lifecycle-subject-header"
  private val usersStreamingSubject       = "users-streaming-value"

  override def run: ZIO[Scope, Any, Any] =
    val listenPapugUpdates = ZIO.serviceWithZIO[PapugListener](_.listenUpdates)
    val listenTaskUpdates  = ZIO.serviceWithZIO[TaskListener](_.listenUpdates)

    val runHTTPServer =
      for
        routes <- ZIO.serviceWith[AccountingRoutes](_.routes)
        _      <- Server.serve(routes.toHttpApp)
      yield ()

    val app = runHTTPServer.raceAll(Chunk(listenPapugUpdates, listenTaskUpdates))

    val consumerSettings = ZLayer.succeed(ConsumerSettings(brokers).withGroupId("accounting"))
    val transactor =
      ZLayer.scoped {
        val config = new HikariConfig()
        config.setDriverClassName("org.postgresql.Driver")
        config.setJdbcUrl("jdbc:postgresql://postgres:5432/accounting")
        config.setUsername("accounting")
        config.setPassword("accounting")
        config.setMaximumPoolSize(32)
        HikariTransactor.fromHikariConfig[Task](config).toScopedZIO
      }

    app.provide(
      AccountingRoutesLive.live,
      KafkaPapugListener.live,
      KafkaTaskListener.live,
      PapugServiceLive.live,
      TaskServiceLive.live,
      RedpandaAvroSchemaRegistry.live,
      AccountingServiceLive.live,
      DoobiePapugRepo.live,
      DoobieTaskRepo.live,
      Server.defaultWithPort(8001),
      Client.default,
      transactor,
      consumerSettings,
      ZLayer.succeed(
        KafkaPapugListener.Config(
          papugsStreamingTopic = usersStreamingTopic,
          papugsStreamingSubject = usersStreamingSubject
        )
      ),
      ZLayer.succeed(
        KafkaTaskListener.Config(
          taskLifecycleTopic = tasksLifecycleTopic,
          taskLifecycleSubjectHeader = tasksLifecycleSubjectHeader,
          taskCreatedSubject = tasksCreatedSubject,
          taskReassignedSubject = tasksReassignedSubject,
          taskCompletedSubject = tasksCompletedSubject
        )
      ),
      ZLayer.succeed(RedpandaAvroSchemaRegistry.Config(schemaRegistry))
    )
