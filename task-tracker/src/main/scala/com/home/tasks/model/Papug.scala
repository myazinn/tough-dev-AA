package com.home.tasks.model

case class Papug(
  id: PapugId,
  email: Email,
  roles: Set[Role]
)
