package com.home.keycloak.acl.adapters

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

import scala.jdk.CollectionConverters.*

import com.home.avro.schema.AvroSchemaRegistry
import com.home.keycloak.acl.adapters.KafkaUserPublisher.Config
import com.home.keycloak.acl.model.*
import com.home.keycloak.acl.service.UserPublisher
import com.sksamuel.avro4s.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader

import zio.*
import zio.kafka.producer.{ Producer, ProducerSettings }
import zio.kafka.serde.Serde

object KafkaUserPublisher:
  case class Config(
    usersStreamingTopic: String,
    usersStreamingSubject: String,
    userRoleUpdatedTopic: String,
    userRoleUpdatedSubject: String
  )

  val live: RLayer[AvroSchemaRegistry & ProducerSettings & Config, KafkaUserPublisher] =
    ZLayer.scoped:
      for
        schemaRegistry   <- ZIO.service[AvroSchemaRegistry]
        producerSettings <- ZIO.service[ProducerSettings]
        producer         <- Producer.make(producerSettings)
        config           <- ZIO.service[Config]
      yield KafkaUserPublisher(schemaRegistry, producer, config)

final case class KafkaUserPublisher(schemaRegistry: AvroSchemaRegistry, producer: Producer, config: Config)
    extends UserPublisher:

  override def publish(user: User): UIO[Unit] =
    encode(user, config.usersStreamingSubject).flatMap:
      case None => ZIO.dieMessage("Failed to encode user, schema not found")
      case Some(value) =>
        val headers   = List(VERSION_HEADER).asJava
        val toProduce = ProducerRecord(config.usersStreamingTopic, null, user.userId, value, headers)
        producer.produce(toProduce, Serde.string, Serde.byteArray).unit.orDie *>
          ZIO.logInfo(s"User published with value ${new String(value)} and size ${value.length}")

  override def publish(user: UserRoleUpdated): UIO[Unit] =
    encode(user, config.userRoleUpdatedSubject).flatMap:
      case None => ZIO.dieMessage("Failed to encode user role updates, schema not found")
      case Some(value) =>
        val headers   = List(VERSION_HEADER).asJava
        val toProduce = ProducerRecord(config.userRoleUpdatedTopic, null, user.userId, value, headers)
        producer.produce(toProduce, Serde.string, Serde.byteArray).unit.orDie *>
          ZIO.logInfo(s"User published with value ${new String(value)} and size ${value.length}")

  private val VERSION = 1
  private val VERSION_HEADER: Header =
    val bb = ByteBuffer.allocate(4)
    bb.putInt(VERSION)
    new RecordHeader("version", bb.array())

  private def encode[U: Encoder](user: U, subject: String): UIO[Option[Array[Byte]]] =
    schemaRegistry
      .getForSubject(subject, VERSION)
      .flatMap:
        case None => ZIO.none
        case Some(schema) =>
          ZIO
            .succeed:
              val os  = new ByteArrayOutputStream()
              val aos = AvroOutputStream.binary(schema, Encoder[U]).to(os).build()
              aos.write(user)
              aos.close()
              Some(os.toByteArray)
  end encode

end KafkaUserPublisher
