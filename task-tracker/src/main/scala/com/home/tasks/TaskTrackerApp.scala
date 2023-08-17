package com.home.tasks

import zio.*
import zio.kafka.consumer.*
import zio.kafka.consumer.diagnostics.Diagnostics
import zio.kafka.serde.Serde

object TaskTrackerApp extends ZIOAppDefault:

  private val brokers = List("redpanda:9092")

  private val usersStreamingTopic  = "users-streaming"
  private val userRoleUpdatedTopic = "users-roles"

  override def run: ZIO[Scope, Any, Any] =
    val consumerSettings = ConsumerSettings(brokers).withGroupId("task-tracker")

    val consumerMessages =
      Consumer
        .plainStream(Subscription.topics(usersStreamingTopic, userRoleUpdatedTopic), Serde.byteArray, Serde.string)
        .runForeach { record =>
          ZIO.debug(s"Received: ${record.value} from ${record.record.topic()}")
        }

    val program = consumerMessages

    program.provide(
      Consumer.live,
      ZLayer.succeed(Diagnostics.NoOp),
      ZLayer.succeed(consumerSettings)
    )
