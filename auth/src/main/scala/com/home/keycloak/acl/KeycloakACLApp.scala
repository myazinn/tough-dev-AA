package com.home.keycloak.acl

import com.home.keycloak.acl.model.*
import io.circe.syntax.*
import io.circe.{ parser, Decoder, Json }
import org.apache.kafka.clients.producer.ProducerRecord

import zio.*
import zio.interop.catz._
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
  private val userRoleUpdatedTopic = "users-roles"

  override def run: ZIO[Scope, Any, Any] =
    val consumeMessages =
      Consumer
        .partitionedStream(Subscription.topics(rawEventsTopic, rawAdminEventsTopic), Serde.byteArray, Serde.string)
        .mapZIOParUnordered(128): (tp, stream) =>
          val processor: String => RIO[Producer, Any] =
            if tp.topic == rawEventsTopic then processRawEvents
            else processRawAdminEvents

          stream.runForeach(record => processor(record.value) *> record.offset.commit)
        .runDrain

    val program = consumeMessages

    program.provide(
      Producer.live,
      Consumer.live,
      ZLayer.succeed(Diagnostics.NoOp),
      ZLayer.succeed(producerSettings),
      ZLayer.succeed(consumerSettings)
    )

  private val storage: UserRepository =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(ZIO.service[UserRepository].provide(DoobieUserRepository.live)).getOrThrow()
    }

  private def processRawEvents(rawEvent: String): RIO[Producer, Any] =
    ZIO
      .foreach(parser.parse(rawEvent).flatMap(_.as[RawEvent]).toOption) { event =>
        event.`type` match
          case "REGISTER" =>
            ZIO.succeed:
              event.details
                .as[RegisterDetails]
                .toOption
                .map: details =>
                  UserCUD(
                    userId = event.user_id,
                    username = details.username,
                    email = details.email,
                    firstName = details.first_name,
                    lastName = details.last_name,
                    roles = Set.empty
                  )

          case "UPDATE_PROFILE" =>
            storage
              .get(event.user_id)
              .map: existing =>
                event.details
                  .as[UpdateProfileDetails]
                  .toOption
                  .flatMap: details =>
                    for
                      username  <- details.updated_username.orElse(existing.map(_.username))
                      email     <- details.updated_email.orElse(existing.map(_.email))
                      firstName <- details.updated_first_name.orElse(existing.map(_.firstName))
                      lastName  <- details.updated_last_name.orElse(existing.map(_.lastName))
                      roles = existing.map(_.roles).getOrElse(Set.empty)
                    yield UserCUD(
                      userId = event.user_id,
                      username = username,
                      email = email,
                      firstName = firstName,
                      lastName = lastName,
                      roles = roles
                    )

          case _ => ZIO.none
      }
      .map(_.flatten)
      .flatMap:
        ZIO.foreachDiscard(_): user =>
          val toProduce = ProducerRecord(usersStreamingTopic, user.userId, user.asJson.noSpaces)
          Producer.produce(toProduce, Serde.string, Serde.string) *> storage.save(user)

  private def processRawAdminEvents(rawAdminEvent: String): RIO[Producer, Any] =
    def processUserOperation(event: AdminRawEvent)(existing: Option[UserCUD]): Option[UserCUD] =
      event.operation_type match
        case "CREATE" | "UPDATE" =>
          parser
            .parse(event.representation)
            .flatMap(_.as[UserUpdateByAdmin])
            .toOption
            .map: update =>
              UserCUD(
                userId = event.userId,
                username = update.username,
                email = update.email,
                firstName = update.firstName,
                lastName = update.lastName,
                roles = existing.map(_.roles).getOrElse(Set.empty)
              )
        case _ => None
    end processUserOperation

    def processRoleMappingOperation(event: AdminRawEvent)(existing: Option[UserCUD]): Option[UserCUD] =
      event.operation_type match
        case "CREATE" | "DELETE" =>
          val update: (Set[String], Set[String]) => Set[String] = (current, updates) =>
            event.operation_type match
              case "CREATE" => current ++ updates
              case "DELETE" => current -- updates

          parser
            .parse(event.representation)
            .flatMap(_.as[List[UserRoleUpdateByAdmin]])
            .toOption
            .zip(existing)
            .map: (updates, existing) =>
              UserCUD(
                userId = event.userId,
                username = existing.username,
                email = existing.email,
                firstName = existing.firstName,
                lastName = existing.lastName,
                roles = update(existing.roles, updates.map(_.name).toSet)
              )
        case _ => None
    end processRoleMappingOperation

    ZIO
      .foreach(parser.parse(rawAdminEvent).flatMap(_.as[AdminRawEvent]).toOption) { event =>
        storage
          .get(event.userId)
          .map: existing =>
            event.resource_type match
              case "USER"               => processUserOperation(event)(existing).map(_ -> false)
              case "REALM_ROLE_MAPPING" => processRoleMappingOperation(event)(existing).map(_ -> true)
              case _                    => None
      }
      .map(_.flatten)
      .flatMap:
        ZIO.foreachDiscard(_): (user, isRoleUpdated) =>
          val cudEvent = ProducerRecord(usersStreamingTopic, user.userId, user.asJson.noSpaces)
          val updatedRoleEvent =
            if isRoleUpdated then
              Chunk(
                ProducerRecord(
                  userRoleUpdatedTopic,
                  user.userId,
                  UserRoleUpdatedEvent(user.userId, user.roles).asJson.noSpaces
                )
              )
            else Chunk.empty

          Producer.produceChunk(cudEvent +: updatedRoleEvent, Serde.string, Serde.string) *>
            storage.save(user)
  end processRawAdminEvents

end KeycloakACLApp

case class RawEvent(`type`: String, user_id: String, details: Json) derives Decoder
case class UpdateProfileDetails(
  updated_username: Option[String],
  updated_email: Option[String],
  updated_first_name: Option[String],
  updated_last_name: Option[String]
) derives Decoder
case class RegisterDetails(username: String, email: String, first_name: String, last_name: String) derives Decoder

case class AdminRawEvent(
  resource_type: String,
  operation_type: String,
  representation: String,
  resource_path: String
) derives Decoder {
  def userId: String = resource_path.stripPrefix("users/").stripSuffix("/role-mappings/realm")
}

case class UserUpdateByAdmin(
  username: String,
  email: String,
  firstName: String,
  lastName: String
) derives Decoder

case class UserRoleUpdateByAdmin(name: String) derives Decoder
