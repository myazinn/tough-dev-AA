package com.home.keycloak.acl.model

import io.circe.Decoder

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
