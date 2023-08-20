package com.home.keycloak.acl

import com.home.avro.schema.RedpandaAvroSchemaRegistry
import com.home.keycloak.acl.adapters.*
import com.home.keycloak.acl.repo.{ DoobieUserRepository, UserRepository }
import com.home.keycloak.acl.service.*
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.*

import zio.*
import zio.http.Client
import zio.interop.catz.*
import zio.kafka.consumer.*
import zio.kafka.producer.*

object KeycloakACLApp extends ZIOAppDefault:
  private val brokers        = List("redpanda:9092")
  private val schemaRegistry = "http://redpanda:18081"

  private val producerSettings = ProducerSettings(brokers)
  private val consumerSettings = ConsumerSettings(brokers).withGroupId("auth-acl-consumer")

  private val rawEventsTopic      = "keycloak-raw-events"
  private val rawAdminEventsTopic = "keycloak-raw-admin-events"

  private val usersStreamingTopic  = "users-streaming"
  private val userRoleUpdatedTopic = "users-roles"

  private val usersStreamingSubject  = "users-streaming-value"
  private val userRoleUpdatedSubject = "users-roles-value"

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
      RedpandaAvroSchemaRegistry.live,
      Client.default,
      ZLayer.scoped(transactor),
      ZLayer.succeed(producerSettings),
      ZLayer.succeed(consumerSettings),
      ZLayer.succeed(KafkaRawEventsListener.Config(rawEventsTopic, rawAdminEventsTopic)),
      ZLayer.succeed {
        KafkaUserPublisher.Config(
          usersStreamingTopic = usersStreamingTopic,
          usersStreamingSubject = usersStreamingSubject,
          userRoleUpdatedTopic = userRoleUpdatedTopic,
          userRoleUpdatedSubject = userRoleUpdatedSubject
        )
      },
      ZLayer.succeed(RedpandaAvroSchemaRegistry.Config(schemaRegistry))
    )
  end run

end KeycloakACLApp
