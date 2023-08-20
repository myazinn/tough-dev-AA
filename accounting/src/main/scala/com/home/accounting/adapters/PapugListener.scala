package com.home.accounting.adapters

import java.nio.ByteBuffer

import scala.util.*

import com.home.accounting.adapters.KafkaPapugListener.Config
import com.home.accounting.model.*
import com.home.accounting.service.PapugService
import com.home.avro.schema.AvroSchemaRegistry
import com.sksamuel.avro4s.*
import org.apache.avro.Schema

import zio.*
import zio.kafka.consumer.{ CommittableRecord, Consumer, ConsumerSettings, Subscription }
import zio.kafka.serde.Serde

trait PapugListener:
  def listenUpdates: UIO[Unit]

object KafkaPapugListener:
  case class Config(papugsStreamingTopic: String, papugsStreamingSubject: String)

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
      .plainStream(Subscription.topics(config.papugsStreamingTopic), Serde.byteArray, Serde.byteArray)
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
    findSchema(record)
      .flatMap: schema =>
        ZIO
          .attempt(AvroInputStream.binary[Papug].from(record.value).build(schema).iterator)
          .mapError(err => new IllegalArgumentException(s"Failed to parse papug", err))
      .flatMap: papugs =>
        if papugs.hasNext
        then ZIO.succeed(papugs.next())
        else ZIO.fail(new IllegalArgumentException("There are no papugs in the event"))
  end decode

  private def findSchema(record: CommittableRecord[_, Array[Byte]]): IO[IllegalArgumentException, Schema] =
    Option(record.record.headers().lastHeader("version")) match
      case None => ZIO.fail(new IllegalArgumentException("Couldn't extract schema version"))
      case Some(versionBytes) =>
        val subject = config.papugsStreamingSubject
        val version = ByteBuffer.wrap(versionBytes.value()).getInt()
        schemaRegistry
          .getForSubject(subject, version)
          .someOrFail(new IllegalArgumentException(s"Couldn't find schema $subject for version $version"))

  private given Decoder[PapugId] = Decoder[String].map(PapugId(_))
  private given Decoder[Email]   = Decoder[String].map(Email(_))

end KafkaPapugListener
