package com.home.keycloak.acl.adapters

import com.home.keycloak.acl.adapters.KafkaRawEventsListener.Config
import com.home.keycloak.acl.model.{ AdminRawEvent, RawEvent }
import com.home.keycloak.acl.service.RawEventsService
import io.circe.parser

import zio.*
import zio.kafka.consumer.{ Consumer, ConsumerSettings, Subscription }
import zio.kafka.serde.Serde

trait RawEventsListener:
  def listenUpdates: UIO[Unit]

object KafkaRawEventsListener:
  case class Config(rawEventsTopic: String, rawAdminEventsTopic: String)

  val live: RLayer[ConsumerSettings & RawEventsService & Config, KafkaRawEventsListener] =
    ZLayer.scoped:
      for
        consumerSettings <- ZIO.service[ConsumerSettings]
        consumer         <- Consumer.make(consumerSettings)
        rawEventsService <- ZIO.service[RawEventsService]
        config           <- ZIO.service[Config]
      yield KafkaRawEventsListener(consumer, rawEventsService, config)

final case class KafkaRawEventsListener(consumer: Consumer, rawEventsService: RawEventsService, config: Config)
    extends RawEventsListener:

  override def listenUpdates: UIO[Unit] =
    consumer
      .partitionedStream(
        Subscription.topics(config.rawEventsTopic, config.rawAdminEventsTopic),
        Serde.byteArray,
        Serde.string
      )
      .mapZIOParUnordered(128): (tp, stream) =>
        val processor: String => UIO[Any] =
          if tp.topic == config.rawEventsTopic
          then processRawEvents
          else processRawAdminEvents

        stream.runForeach(record => processor(record.value) *> record.offset.commit)
      .runDrain
      .orDie

  private def processRawEvents(rawEvent: String): UIO[Any] =
    ZIO
      .succeed(parser.parse(rawEvent).flatMap(_.as[RawEvent]).toOption)
      .flatMap:
        case Some(rawEvent) => rawEventsService.process(rawEvent)
        case None           => ZIO.logWarning(s"Failed to parse raw event: $rawEvent")

  private def processRawAdminEvents(rawAdminEvent: String): UIO[Any] =
    ZIO
      .succeed(parser.parse(rawAdminEvent).flatMap(_.as[AdminRawEvent]).toOption)
      .flatMap:
        case Some(adminRawEvent) => rawEventsService.process(adminRawEvent)
        case None                => ZIO.logWarning(s"Failed to parse admin raw event: $rawAdminEvent")

end KafkaRawEventsListener
