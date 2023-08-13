package com.home.keycloak.acl

import io.circe.syntax.*
import io.circe.{ parser, Decoder, Encoder, Json }
import org.apache.kafka.clients.producer.ProducerRecord

import zio.*
import zio.kafka.consumer.*
import zio.kafka.consumer.diagnostics.Diagnostics
import zio.kafka.producer.*
import zio.kafka.serde.Serde

object KeycloakACLApp extends ZIOAppDefault:
  private val brokers = List("redpanda:9092")

  private val producerSettings = ProducerSettings(brokers)
  private val consumerSettings = ConsumerSettings(brokers).withGroupId("auth-acl-consumer")

  private val rawEventsTopic      = "keycloak-raw-events"
  private val rawAdminEventsTopic = "keycloak-raw-admin-events"

  private val usersStreamingTopic  = "users-streaming"
  private val userRoleUpdatedTopic = "user-role-updates"

  override def run: ZIO[Scope, Any, Any] =
    val consumeMessages =
      Consumer
        .partitionedStream(Subscription.topics(rawEventsTopic, rawAdminEventsTopic), Serde.byteArray, Serde.string)
        .mapZIOParUnordered(128): (tp, stream) =>
          val processor: Chunk[String] => RIO[Producer, Any] =
            if tp.topic == rawEventsTopic then processRawEvents
            else processRawAdminEvents

          stream
            .mapChunksZIO(records => processor(records.map(_.value)).as(records.map(_.offset)))
            .runForeachChunk(_.last.commit)
        .runDrain

    val program = consumeMessages

    program.provide(
      Producer.live,
      Consumer.live,
      ZLayer.succeed(Diagnostics.NoOp),
      ZLayer.succeed(producerSettings),
      ZLayer.succeed(consumerSettings)
    )

  private def processRawEvents(rawEvents: Chunk[String]): RIO[Producer, Any] =
    val events =
      rawEvents
        .flatMap(parser.parse(_).flatMap(_.as[RawEvent]).toOption)
        .flatMap: raw =>
          raw.`type` match
            case "REGISTER" =>
              raw.details
                .as[RegisterDetails]
                .toOption
                .map(details => UserCUD(userId = raw.user_id, username = Some(details.username)))
            case "UPDATE_PROFILE" =>
              raw.details
                .as[UpdateProfileDetails]
                .toOption
                .map(details => UserCUD(userId = raw.user_id, username = details.updated_username))
            case _ => None
        .map: user =>
          new ProducerRecord(usersStreamingTopic, user.userId, user.asJson.noSpaces)

    Producer.produceChunk(events, Serde.string, Serde.string)

  private def processRawAdminEvents(rawAdminEvents: Chunk[String]): RIO[Producer, Any] =
    val events =
      val _ = (rawAdminEvents, userRoleUpdatedTopic) // todo actually extract role updates
      Chunk.empty[ProducerRecord[String, String]]

    Producer.produceChunk(events, Serde.string, Serde.string)

end KeycloakACLApp

case class UserCUD(userId: String, username: Option[String])
object UserCUD:
  given Encoder[UserCUD] =
    Encoder.instance: user =>
      Json.obj("user_id" -> user.userId.asJson, "username" -> user.username.asJson)

case class RawEvent(`type`: String, user_id: String, details: Json) derives Decoder
case class UpdateProfileDetails(updated_username: Option[String]) derives Decoder
case class RegisterDetails(username: String) derives Decoder

case class AdminRawEvent(`type`: String, user_id: String, details: Json) derives Decoder
