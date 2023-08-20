package com.home.tasks.model

import com.sksamuel.avro4s.AvroName

case class Papug(
  @AvroName("user_id") id: PapugId,
  @AvroName("email") email: Email,
  @AvroName("roles") roles: Set[Role]
)
