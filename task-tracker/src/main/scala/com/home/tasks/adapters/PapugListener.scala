package com.home.tasks.adapters

import java.nio.ByteBuffer

import scala.util.*

import com.home.avro.schema.AvroSchemaRegistry
import com.home.tasks.adapters.KafkaPapugListener.Config
import com.home.tasks.model.*
import com.home.tasks.service.PapugService
import com.sksamuel.avro4s.*
import org.apache.avro.Schema

import zio.*
import zio.kafka.consumer.{ CommittableRecord, Consumer, ConsumerSettings, Subscription }
import zio.kafka.serde.Serde

trait PapugListener:
  def listenUpdates: UIO[Unit]

object KafkaPapugListener:
  case class Config(usersStreamingTopic: String, usersStreamingSubject: String)

  val live: URLayer[AvroSchemaRegistry & ConsumerSettings & PapugService & Config, KafkaPapugListener] =
    ZLayer.scoped:
      for
        schemaRegistry   <- ZIO.service[AvroSchemaRegistry]
        consumerSettings <- ZIO.service[ConsumerSettings]
        consumer         <- Consumer.make(consumerSettings).orDie
        papugService     <- ZIO.service[PapugService]
        config           <- ZIO.service[Config]
      yield KafkaPapugListener(schemaRegistry, consumer, papugService, config)

final case class KafkaPapugListener(
  schemaRegistry: AvroSchemaRegistry,
  consumer: Consumer,
  papugService: PapugService,
  config: Config
) extends PapugListener:

  override def listenUpdates: UIO[Unit] =
    consumer
      .plainStream(Subscription.topics(config.usersStreamingTopic), Serde.byteArray, Serde.byteArray)
      .runForeach: record =>
        val process =
          decode(record)
            .flatMap(papugService.upsert)
            .catchAll: err =>
              ZIO.logError(s"Something went wrong when parsing papug: $err")

        ZIO.logInfo(s"processing $record") *> process *> record.offset.commit
      .orDie
      .tapErrorCause(ZIO.logErrorCause(s"Something really bad happpened", _))
  end listenUpdates

  private def decode(record: CommittableRecord[_, Array[Byte]]): IO[IllegalArgumentException, Papug] =
    findSchema(record).flatMap: schema =>
      val papugs = AvroInputStream.binary[Papug].from(record.value).build(schema).iterator

      if papugs.hasNext
      then ZIO.attempt(papugs.next()).mapError(err => new IllegalArgumentException(s"Failed to parse papug", err))
      else ZIO.fail(new IllegalArgumentException("There are no papugs in the event"))
  end decode

  private def findSchema(record: CommittableRecord[_, Array[Byte]]): IO[IllegalArgumentException, Schema] =
    Option(record.record.headers().lastHeader("version")) match
      case None => ZIO.fail(new IllegalArgumentException("Couldn't extract schema ID"))
      case Some(versionBytes) =>
        val subject = config.usersStreamingSubject
        val version = ByteBuffer.wrap(versionBytes.value()).getInt()
        schemaRegistry
          .getForSubject(subject, version)
          .someOrFail(new IllegalArgumentException(s"Couldn't find schema $subject for version $version"))

  private given Decoder[PapugId] = Decoder[String].map(PapugId(_))
  private given Decoder[Email]   = Decoder[String].map(Email(_))
  private given Decoder[Role]    = Decoder[String].map(Role(_))

end KafkaPapugListener
