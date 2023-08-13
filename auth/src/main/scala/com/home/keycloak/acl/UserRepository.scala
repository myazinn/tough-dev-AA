package com.home.keycloak.acl

import com.home.keycloak.acl.model.UserCUD
import com.zaxxer.hikari.HikariConfig
import doobie.Transactor
import doobie.hikari.HikariTransactor
import doobie.implicits.{ toConnectionIOOps, toSqlInterpolator }

import zio.*
import zio.interop.catz.*

trait UserRepository:
  def get(id: String): UIO[Option[UserCUD]]
  def save(user: UserCUD): UIO[Unit]

final case class DoobieUserRepository(transactor: Transactor[Task]) extends UserRepository:

  def get(id: String): UIO[Option[UserCUD]] =
    sql"""SELECT user_id, username, email, first_name, last_name, roles
         | FROM users
         | WHERE user_id = $id
         |""".stripMargin
      .query[UserCUDInternal]
      .map(UserCUDInternal.toUser)
      .option
      .transact(transactor)
      .orDie

  def save(user: UserCUD): UIO[Unit] =
    val u = UserCUDInternal.fromUser(user)
    sql"""INSERT INTO users (user_id, username, email, first_name, last_name, roles)
         | VALUES (${u.user_id}, ${u.username}, ${u.email}, ${u.first_name}, ${u.last_name}, ${u.roles})
         | ON CONFLICT (user_id)
         |  DO UPDATE SET
         |    username = ${u.username},
         |    email = ${u.email},
         |    first_name = ${u.first_name},
         |    last_name = ${u.last_name},
         |    roles = ${u.roles}
         |""".stripMargin.update.run
      .transact(transactor)
      .unit
      .orDie

  private case class UserCUDInternal(
    user_id: String,
    username: String,
    email: String,
    first_name: String,
    last_name: String,
    roles: String
  )

  private object UserCUDInternal:
    def fromUser(user: UserCUD): UserCUDInternal =
      UserCUDInternal(
        user_id = user.userId,
        username = user.username,
        email = user.email,
        first_name = user.firstName,
        last_name = user.lastName,
        roles = user.roles.mkString(",")
      )
    def toUser(user: UserCUDInternal): UserCUD =
      UserCUD(
        userId = user.user_id,
        username = user.username,
        email = user.email,
        firstName = user.first_name,
        lastName = user.last_name,
        roles = user.roles.split(",").toSet.filter(_.nonEmpty)
      )

object DoobieUserRepository:
  val live: TaskLayer[UserRepository] =
    ZLayer.fromZIO:
      (for {
        transactor <- {
          val config = new HikariConfig()
          config.setDriverClassName("org.postgresql.Driver")
          config.setJdbcUrl("jdbc:postgresql://postgres:5432/auth")
          config.setUsername("auth")
          config.setPassword("auth")
          config.setMaximumPoolSize(32)
          HikariTransactor.fromHikariConfig[Task](config).toScopedZIO
        }
        _ <-
          sql"""CREATE TABLE IF NOT EXISTS users (
               |  user_id TEXT PRIMARY KEY,
               |  username TEXT NOT NULL,
               |  email TEXT NOT NULL,
               |  first_name TEXT NOT NULL,
               |  last_name TEXT NOT NULL,
               |  roles TEXT NOT NULL
               |)
               |""".stripMargin.update.run
            .transact(transactor)
            .unit
            .orDie
      } yield DoobieUserRepository(transactor)).provide(ZLayer.succeed(Scope.global))
