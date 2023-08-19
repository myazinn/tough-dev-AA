package com.home.keycloak.acl.service

import com.home.keycloak.acl.model.*
import com.home.keycloak.acl.repo.UserRepository
import io.circe.parser

import zio.*

trait RawEventsService:
  def process(event: RawEvent): UIO[Unit]
  def process(event: AdminRawEvent): UIO[Unit]

object RawEventsServiceLive:
  val live: URLayer[UserPublisher & UserRepository, RawEventsServiceLive] =
    ZLayer.fromFunction(RawEventsServiceLive.apply _)

final case class RawEventsServiceLive(publisher: UserPublisher, storage: UserRepository) extends RawEventsService:
  override def process(event: RawEvent): UIO[Unit] =
    ZIO
      .suspendSucceed:
        event.`type` match
          case "REGISTER" =>
            ZIO.succeed:
              event.details
                .as[RegisterDetails]
                .toOption
                .map: details =>
                  User(
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
                    yield User(
                      userId = event.user_id,
                      username = username,
                      email = email,
                      firstName = firstName,
                      lastName = lastName,
                      roles = roles
                    )

          case _ => ZIO.none
      .flatMap:
        ZIO.foreachDiscard(_)(user => publisher.publish(user) *> storage.save(user))
  end process

  override def process(event: AdminRawEvent): UIO[Unit] =
    def processUserOperation(event: AdminRawEvent)(existing: Option[User]): Option[User] =
      event.operation_type match
        case "CREATE" | "UPDATE" =>
          parser
            .parse(event.representation)
            .flatMap(_.as[UserUpdateByAdmin])
            .toOption
            .map: update =>
              User(
                userId = event.userId,
                username = update.username,
                email = update.email,
                firstName = update.firstName,
                lastName = update.lastName,
                roles = existing.map(_.roles).getOrElse(Set.empty)
              )
        case _ => None
    end processUserOperation

    def processRoleMappingOperation(event: AdminRawEvent)(existing: Option[User]): Option[User] =
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
              User(
                userId = event.userId,
                username = existing.username,
                email = existing.email,
                firstName = existing.firstName,
                lastName = existing.lastName,
                roles = update(existing.roles, updates.map(_.name).toSet)
              )
        case _ => None
    end processRoleMappingOperation

    storage
      .get(event.userId)
      .map: existing =>
        event.resource_type match
          case "USER"               => processUserOperation(event)(existing).map(_ -> false)
          case "REALM_ROLE_MAPPING" => processRoleMappingOperation(event)(existing).map(_ -> true)
          case _                    => None
      .flatMap:
        ZIO.foreachDiscard(_): (user, isRoleUpdated) =>
          publisher
            .publish(user)
            .zipPar(ZIO.when(isRoleUpdated)(publisher.publish(UserRoleUpdated(user.userId, user.roles)))) *>
            storage.save(user)
  end process

end RawEventsServiceLive
