package com.home.keycloak.acl.model

import io.circe.syntax.*
import io.circe.{ Encoder, Json }

case class UserCUD(
  userId: String,
  username: String,
  email: String,
  firstName: String,
  lastName: String,
  roles: Set[String]
)

object UserCUD:
  given Encoder[UserCUD] =
    Encoder.instance: user =>
      Json.obj(
        "user_id"    -> user.userId.asJson,
        "username"   -> user.username.asJson,
        "email"      -> user.username.asJson,
        "first_name" -> user.firstName.asJson,
        "last_name"  -> user.lastName.asJson,
        "roles"      -> user.roles.asJson
      )
