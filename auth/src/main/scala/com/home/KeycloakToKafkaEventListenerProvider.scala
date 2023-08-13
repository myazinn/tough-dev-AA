package com.home

import java.util

import scala.jdk.CollectionConverters.*

import io.circe.Json
import io.circe.syntax.*
import org.keycloak.events.admin.AdminEvent
import org.keycloak.events.{ Event, EventListenerProvider }

import zio.*
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde

class KeycloakToKafkaEventListenerProvider(topic: String, producer: Producer, runtime: Runtime[Any])
    extends EventListenerProvider:

  private implicit val unsafe: Unsafe = Unsafe.unsafe(identity)

  override def onEvent(event: Event): Unit =
    val encodedEvent =
      Json
        .obj(
          "id"        -> Option(event.getId).asJson,
          "time"      -> Option(event.getTime).asJson,
          "type"      -> Option(event.getType).map(_.toString).asJson,
          "realmId"   -> Option(event.getRealmId).asJson,
          "clientId"  -> Option(event.getClientId).asJson,
          "userId"    -> Option(event.getUserId).asJson,
          "sessionId" -> Option(event.getSessionId).asJson,
          "ipAddress" -> Option(event.getIpAddress).asJson,
          "error"     -> Option(event.getError).asJson,
          "details"   -> Option(event.getDetails).getOrElse(new util.HashMap()).asScala.toMap.asJson
        )
        .noSpaces

    val produce =
      ZIO.logInfo(s"Producing event to Kafka $encodedEvent") *>
        producer.produce(topic, event.getUserId, encodedEvent, Serde.string, Serde.string)

    runtime.unsafe.run(produce)

  override def onEvent(event: AdminEvent, includeRepresentation: Boolean): Unit =
    runtime.unsafe.run(ZIO.logInfo(s"Received admin event: $event, skipping"))

  override def close(): Unit =
    runtime.unsafe.run(ZIO.logInfo("Closing the event listener provider"))
