package com.home.keycloak.acl.model

import io.circe.syntax.*
import io.circe.{ Encoder, Json }

case class User(
  userId: String,
  username: String,
  email: String,
  firstName: String,
  lastName: String,
  roles: Set[String]
)

object User:
  given Encoder[User] =
    Encoder.instance: user =>
      Json.obj(
        "user_id"    -> user.userId.asJson,
        "username"   -> user.username.asJson,
        "email"      -> user.email.asJson,
        "first_name" -> user.firstName.asJson,
        "last_name"  -> user.lastName.asJson,
        "roles"      -> user.roles.asJson
      )
