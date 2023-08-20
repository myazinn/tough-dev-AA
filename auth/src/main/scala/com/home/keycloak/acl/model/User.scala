package com.home.keycloak.acl.model

import com.sksamuel.avro4s.AvroName

case class User(
  @AvroName("user_id") userId: String,
  @AvroName("username") username: String,
  @AvroName("email") email: String,
  @AvroName("first_name") firstName: String,
  @AvroName("last_name") lastName: String,
  @AvroName("roles") roles: Set[String]
)
