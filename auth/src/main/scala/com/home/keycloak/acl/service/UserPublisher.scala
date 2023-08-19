package com.home.keycloak.acl.service

import com.home.keycloak.acl.model.{ User, UserRoleUpdated }

import zio.UIO

trait UserPublisher:
  def publish(user: User): UIO[Unit]
  def publish(user: UserRoleUpdated): UIO[Unit]
