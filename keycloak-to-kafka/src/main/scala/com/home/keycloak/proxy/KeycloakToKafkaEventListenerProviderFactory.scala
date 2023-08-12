package com.home.keycloak.proxy

import org.keycloak.Config
import org.keycloak.events.{ EventListenerProvider, EventListenerProviderFactory }
import org.keycloak.models.{ KeycloakSession, KeycloakSessionFactory }

import zio.*
import zio.kafka.producer.{ Producer, ProducerSettings }

class KeycloakToKafkaEventListenerProviderFactory extends EventListenerProviderFactory {

  private implicit val unsafe: Unsafe = Unsafe.unsafe(identity)

  private var runtime: Runtime[Any]              = _
  private var producerSettings: ProducerSettings = _
  private var producer: Producer                 = _

  override def create(session: KeycloakSession): EventListenerProvider =
    new KeycloakToKafkaEventListenerProvider("keycloak-raw-events", "keycloak-raw-admin-events", producer, runtime)

  override def init(config: Config.Scope): Unit =
    runtime = Runtime.default
    producerSettings = ProducerSettings(List("redpanda:9092"))
    producer = runtime.unsafe.run(Producer.make(producerSettings).provide(ZLayer.succeed(Scope.global))).getOrThrow()

  override def postInit(factory: KeycloakSessionFactory): Unit = ()

  override def close(): Unit = ()

  override def getId: String = "keycloak-to-kafka"

}
