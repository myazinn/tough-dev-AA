package com.home.keycloak.acl.model

import io.circe.syntax.*
import io.circe.{ Encoder, Json }

case class UserRoleUpdatedEvent(userId: String, roles: Set[String])
object UserRoleUpdatedEvent:
  given Encoder[UserRoleUpdatedEvent] =
    Encoder.instance: event =>
      Json.obj(
        "user_id" -> event.userId.asJson,
        "roles"   -> event.roles.asJson
      )
