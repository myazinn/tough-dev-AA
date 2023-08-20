package com.home.avro.schema

import scala.util.*

import com.home.avro.schema.RedpandaAvroSchemaRegistry.Config
import io.circe.*
import org.apache.avro.Schema

import zio.*
import zio.http.*

trait AvroSchemaRegistry:
  def getById(id: Int): UIO[Option[Schema]]
  def getForSubject(subject: String, version: Int): UIO[Option[Schema]]

object RedpandaAvroSchemaRegistry:
  case class Config(url: String)

  val live: URLayer[Config & Client, RedpandaAvroSchemaRegistry] =
    ZLayer.fromZIO:
      for
        client         <- ZIO.service[Client]
        config         <- ZIO.service[Config]
        byIdCache      <- Ref.make(Map.empty[Int, Schema])
        bySubjectCache <- Ref.make(Map.empty[(String, Int), Schema])
      yield RedpandaAvroSchemaRegistry(client, config, byIdCache, bySubjectCache)

final case class RedpandaAvroSchemaRegistry(
  httpClient: Client,
  config: Config,
  byIdCache: Ref[Map[Int, Schema]],
  bySubjectCache: Ref[Map[(String, Int), Schema]]
) extends AvroSchemaRegistry:
  private val schemaParser = new Schema.Parser()

  override def getById(id: Int): UIO[Option[Schema]] =
    val fromCache = byIdCache.get.map(_.get(id))
    val fromRegistry =
      ZIO
        .scoped(httpClient.request(Request.get(s"${config.url}/schemas/ids/$id")))
        .flatMap: response =>
          response.status match
            case Status.NotFound =>
              ZIO.none
            case Status.Ok =>
              response.body.asString
                .flatMap: body =>
                  ZIO
                    .succeed(parser.parse(body).flatMap(_.as[GetByIdResponse]))
                    .flatMap:
                      case Right(response) =>
                        ZIO.some(schemaParser.parse(response.schema))
                      case Left(err) =>
                        ZIO.logError(s"Couldn't parse response from schema registry: $err") *> ZIO.none
                .tap(ZIO.foreachDiscard(_)(schema => byIdCache.update(_.updated(id, schema))))
            case status =>
              ZIO.die:
                new IllegalStateException(s"Couldn't fetch schema from schema registry, got unexpected status: $status")
        .orDie

    fromCache.flatMap:
      case Some(schema) => ZIO.some(schema)
      case None         => fromRegistry
  end getById

  override def getForSubject(subject: String, version: Int): UIO[Option[Schema]] =
    val fromCache = bySubjectCache.get.map(_.get((subject, version)))
    val fromRegistry =
      ZIO
        .scoped(httpClient.request(Request.get(s"${config.url}/subjects/$subject/versions/$version")))
        .flatMap: response =>
          response.status match
            case Status.NotFound =>
              ZIO.none
            case Status.Ok =>
              response.body.asString.flatMap: body =>
                ZIO
                  .succeed(parser.parse(body).flatMap(_.as[GetForSubjectResponse]))
                  .flatMap:
                    case Right(response) =>
                      ZIO.some(response)
                    case Left(err) =>
                      ZIO.logError(s"Couldn't parse response from schema registry: $err") *> ZIO.none
                  .flatMap: response =>
                    response
                      .map: response =>
                        val schema = schemaParser.parse(response.schema)
                        bySubjectCache
                          .update(_.updated((subject, version), schema))
                          .zipPar(byIdCache.update(_.updated(response.id, schema)))
                          .as(schema)
                      .map(_.asSome)
                      .getOrElse(ZIO.none)
            case status =>
              ZIO.die:
                new IllegalStateException(s"Couldn't fetch schema from schema registry, got unexpected status: $status")
        .orDie

    fromCache.flatMap:
      case Some(schema) => ZIO.some(schema)
      case None         => fromRegistry
  end getForSubject

  private case class GetByIdResponse(schema: String) derives Decoder
  private case class GetForSubjectResponse(id: Int, schema: String) derives Decoder

end RedpandaAvroSchemaRegistry
