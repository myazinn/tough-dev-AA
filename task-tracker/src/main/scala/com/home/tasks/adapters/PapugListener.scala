package com.home.tasks.adapters

import com.home.tasks.adapters.KafkaPapugListener.Config
import com.home.tasks.model.*
import com.home.tasks.service.PapugService
import io.circe.*

import zio.*
import zio.kafka.consumer.{ Consumer, ConsumerSettings, Subscription }
import zio.kafka.serde.Serde

trait PapugListener:
  def listenUpdates: UIO[Unit]

object KafkaPapugListener:
  case class Config(usersStreamingTopic: String)

  val live: URLayer[ConsumerSettings & PapugService & Config, KafkaPapugListener] =
    ZLayer.scoped:
      for
        consumerSettings <- ZIO.service[ConsumerSettings]
        consumer         <- Consumer.make(consumerSettings).orDie
        papugService     <- ZIO.service[PapugService]
        config           <- ZIO.service[Config]
      yield KafkaPapugListener(consumer, papugService, config)

final case class KafkaPapugListener(consumer: Consumer, papugService: PapugService, config: Config)
    extends PapugListener:

  override def listenUpdates: UIO[Unit] =
    consumer
      .plainStream(Subscription.topics(config.usersStreamingTopic), Serde.byteArray, Serde.string)
      .runForeach: record =>
        val process =
          parser.parse(record.value).flatMap(_.as[Papug]) match
            case Left(error) =>
              ZIO.logError(s"Failed to parse papug: $error")
            case Right(papug) =>
              papugService.upsert(papug)
        process *> record.offset.commit
      .orDie
  end listenUpdates

  private given Decoder[Papug] =
    Decoder.instance { hc =>
      for
        id    <- hc.downField("user_id").as[PapugId]
        email <- hc.downField("email").as[Email]
        roles <- hc.downField("roles").as[Set[Role]]
      yield Papug(id = id, email = email, roles = roles)
    }
  private given Decoder[PapugId] = Decoder.decodeString.map(PapugId(_))
  private given Decoder[Email]   = Decoder.decodeString.map(Email(_))
  private given Decoder[Role]    = Decoder.decodeString.map(Role(_))

end KafkaPapugListener
