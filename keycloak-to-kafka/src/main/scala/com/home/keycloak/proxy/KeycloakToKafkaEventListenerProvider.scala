package com.home.keycloak.proxy

import java.util

import scala.jdk.CollectionConverters.*

import io.circe.Json
import io.circe.syntax.*
import org.keycloak.events.admin.{ AdminEvent, AuthDetails }
import org.keycloak.events.{ Event, EventListenerProvider }

import zio.*
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde

class KeycloakToKafkaEventListenerProvider(
  eventTopic: String,
  adminEventTopic: String,
  producer: Producer,
  runtime: Runtime[Any]
) extends EventListenerProvider:

  private implicit val unsafe: Unsafe = Unsafe.unsafe(identity)

  override def onEvent(event: Event): Unit =
    val encodedEvent =
      Json
        .obj(
          "id"         -> Option(event.getId).asJson,
          "time"       -> Option(event.getTime).asJson,
          "type"       -> Option(event.getType).map(_.toString).asJson,
          "realm_id"   -> Option(event.getRealmId).asJson,
          "client_id"  -> Option(event.getClientId).asJson,
          "user_id"    -> Option(event.getUserId).asJson,
          "session_id" -> Option(event.getSessionId).asJson,
          "ip_address" -> Option(event.getIpAddress).asJson,
          "error"      -> Option(event.getError).asJson,
          "details"    -> Option(event.getDetails).getOrElse(new util.HashMap()).asScala.toMap.asJson
        )
        .noSpaces

    runtime.unsafe.run:
      producer.produce(eventTopic, event.getUserId, encodedEvent, Serde.string, Serde.string)

  override def onEvent(event: AdminEvent, includeRepresentation: Boolean): Unit =
    val encodedEvent =
      Json
        .obj(
          "id"             -> Option(event.getId).asJson,
          "time"           -> Option(event.getTime).asJson,
          "realm_id"       -> Option(event.getRealmId).asJson,
          "auth_details"   -> Option(event.getAuthDetails).map(authDetailsAsJson).asJson,
          "resource_type"  -> Option(event.getResourceTypeAsString).asJson,
          "operation_type" -> Option(event.getOperationType).map(_.toString).asJson,
          "resource_path"  -> Option(event.getResourcePath).asJson,
          "representation" -> Option(event.getRepresentation).asJson,
          "error"          -> Option(event.getError).asJson
        )
        .noSpaces

    runtime.unsafe.run:
      producer.produce(adminEventTopic, event.getAuthDetails.getUserId, encodedEvent, Serde.string, Serde.string)

  private def authDetailsAsJson(authDetails: AuthDetails): Json =
    Json.obj(
      "realm_id"   -> authDetails.getRealmId.asJson,
      "client_id"  -> authDetails.getClientId.asJson,
      "user_id"    -> authDetails.getUserId.asJson,
      "ip_address" -> authDetails.getIpAddress.asJson
    )

  override def close(): Unit =
    runtime.unsafe.run(ZIO.logInfo("Closing the event listener provider"))
