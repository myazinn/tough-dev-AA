package com.home.keycloak.acl

import com.home.keycloak.acl.adapters.*
import com.home.keycloak.acl.repo.{ DoobieUserRepository, UserRepository }
import com.home.keycloak.acl.service.*
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.*

import zio.*
import zio.interop.catz.*
import zio.kafka.consumer.*
import zio.kafka.producer.*

object KeycloakACLApp extends ZIOAppDefault:
  private val brokers = List("redpanda:9092")

  private val producerSettings = ProducerSettings(brokers)
  private val consumerSettings = ConsumerSettings(brokers).withGroupId("auth-acl-consumer")

  private val rawEventsTopic      = "keycloak-raw-events"
  private val rawAdminEventsTopic = "keycloak-raw-admin-events"

  private val usersStreamingTopic  = "users-streaming"
  private val userRoleUpdatedTopic = "users-roles"

  override val run: ZIO[Any, Any, Any] =
    val consumeRawEvents = ZIO.serviceWithZIO[RawEventsListener](_.listenUpdates)

    val program = consumeRawEvents

    val transactor = {
      val config = new HikariConfig()
      config.setDriverClassName("org.postgresql.Driver")
      config.setJdbcUrl("jdbc:postgresql://postgres:5432/auth")
      config.setUsername("auth")
      config.setPassword("auth")
      config.setMaximumPoolSize(32)
      HikariTransactor.fromHikariConfig[Task](config).toScopedZIO
    }

    program.provide(
      KafkaRawEventsListener.live,
      RawEventsServiceLive.live,
      KafkaUserPublisher.live,
      DoobieUserRepository.live,
      ZLayer.scoped(transactor),
      ZLayer.succeed(producerSettings),
      ZLayer.succeed(consumerSettings),
      ZLayer.succeed(KafkaRawEventsListener.Config(rawEventsTopic, rawAdminEventsTopic)),
      ZLayer.succeed(KafkaUserPublisher.Config(usersStreamingTopic, userRoleUpdatedTopic))
    )
  end run

end KeycloakACLApp
