package com.home

import zio.*
import zio.kafka.consumer.*
import zio.kafka.consumer.diagnostics.Diagnostics
import zio.kafka.producer.*
import zio.kafka.serde.Serde

object FromInsideDocker extends ZIOAppDefault:

  override def run: ZIO[Scope, Any, Any] =
    val producerSettings = ProducerSettings(List("redpanda:9092"))
    val consumerSettings = ConsumerSettings(List("redpanda:9092")).withGroupId("task-tracker")

    val topic = "topic_111"

    val produceMessages =
      Ref
        .make(0)
        .flatMap: ref =>
          ref
            .getAndUpdate(_ + 1)
            .flatMap: i =>
              Producer.produce(topic, i, s"value_$i", Serde.int, Serde.string)
            .repeat(Schedule.fixed(5.seconds))

    val consumerMessages =
      Consumer
        .plainStream(Subscription.topics(topic, "keycloak-events"), Serde.byteArray, Serde.string)
        .runForeach: record =>
          ZIO.debug(s"Received: ${record.value} from ${record.record.topic()}")

    val program = produceMessages.zipPar(consumerMessages)

    program.provide(
      Producer.live,
      Consumer.live,
      ZLayer.succeed(Diagnostics.NoOp),
      ZLayer.succeed(producerSettings),
      ZLayer.succeed(consumerSettings)
    )
