package com.home.keycloak.acl.model

import com.sksamuel.avro4s.AvroName

case class UserRoleUpdated(@AvroName("user_id") userId: String, @AvroName("roles") roles: Set[String])
