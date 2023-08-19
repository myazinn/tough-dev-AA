package com.home.keycloak.acl.model

import io.circe.syntax.*
import io.circe.{ Encoder, Json }

case class UserRoleUpdated(userId: String, roles: Set[String])
object UserRoleUpdated:
  given Encoder[UserRoleUpdated] =
    Encoder.instance: event =>
      Json.obj(
        "user_id" -> event.userId.asJson,
        "roles"   -> event.roles.asJson
      )
