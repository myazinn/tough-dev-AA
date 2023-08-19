package com.home.keycloak.acl.model

import io.circe.{ Decoder, Json }

case class RawEvent(`type`: String, user_id: String, details: Json) derives Decoder
case class UpdateProfileDetails(
  updated_username: Option[String],
  updated_email: Option[String],
  updated_first_name: Option[String],
  updated_last_name: Option[String]
) derives Decoder
case class RegisterDetails(username: String, email: String, first_name: String, last_name: String) derives Decoder
