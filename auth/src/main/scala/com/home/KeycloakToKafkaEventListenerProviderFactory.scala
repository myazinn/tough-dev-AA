package com.home

import org.keycloak.Config
import org.keycloak.events.{ EventListenerProvider, EventListenerProviderFactory }
import org.keycloak.models.{ KeycloakSession, KeycloakSessionFactory }

import zio.*
import zio.kafka.producer.{ Producer, ProducerSettings }

class KeycloakToKafkaEventListenerProviderFactory extends EventListenerProviderFactory {

  private implicit val unsafe: Unsafe = Unsafe.unsafe(identity)
  private val runtime                 = Runtime.default

  override def create(session: KeycloakSession): EventListenerProvider = {

    val producerSettings = ProducerSettings(List("redpanda:9092"))
    val producer =
      runtime.unsafe.run(Producer.make(producerSettings).provide(ZLayer.succeed(Scope.global))).getOrThrow()

    new KeycloakToKafkaEventListenerProvider("keycloak-events", producer, zio.Runtime.default)
  }

  override def init(config: Config.Scope): Unit = ()

  override def postInit(factory: KeycloakSessionFactory): Unit = ()

  override def close(): Unit = ()

  override def getId: String = "keycloak-to-kafka"

}
