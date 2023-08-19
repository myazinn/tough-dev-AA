package com.home.keycloak.acl.adapters

import com.home.keycloak.acl.adapters.KafkaUserPublisher.Config
import com.home.keycloak.acl.model.*
import com.home.keycloak.acl.service.UserPublisher
import io.circe.syntax.*
import org.apache.kafka.clients.producer.ProducerRecord

import zio.*
import zio.kafka.producer.{ Producer, ProducerSettings }
import zio.kafka.serde.Serde

object KafkaUserPublisher:
  case class Config(usersStreamingTopic: String, userRoleUpdatedTopic: String)

  val live: RLayer[ProducerSettings & Config, KafkaUserPublisher] =
    ZLayer.scoped:
      for
        producerSettings <- ZIO.service[ProducerSettings]
        producer         <- Producer.make(producerSettings)
        config           <- ZIO.service[Config]
      yield KafkaUserPublisher(producer, config)

final case class KafkaUserPublisher(producer: Producer, config: Config) extends UserPublisher:

  override def publish(user: User): UIO[Unit] =
    val toProduce = ProducerRecord(config.usersStreamingTopic, user.userId, user.asJson.noSpaces)
    producer.produce(toProduce, Serde.string, Serde.string).unit.orDie

  override def publish(user: UserRoleUpdated): UIO[Unit] =
    val toProduce = ProducerRecord(config.userRoleUpdatedTopic, user.userId, user.asJson.noSpaces)
    producer.produce(toProduce, Serde.string, Serde.string).unit.orDie
