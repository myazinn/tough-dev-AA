package com.home.accounting.adapters

import java.nio.ByteBuffer
import java.time.Instant

import com.home.accounting.adapters.KafkaTaskListener.Config
import com.home.accounting.model.*
import com.home.accounting.service.AccountingService
import com.home.avro.schema.AvroSchemaRegistry
import com.sksamuel.avro4s.*
import org.apache.avro.Schema
import org.apache.kafka.common.header.Header

import zio.kafka.consumer.*
import zio.kafka.serde.Serde
import zio.{ Task as _, * }

trait TaskListener:
  def listenUpdates: UIO[Unit]

object KafkaTaskListener:
  case class Config(
    taskLifecycleTopic: String,
    taskLifecycleSubjectHeader: String,
    taskCreatedSubject: String,
    taskReassignedSubject: String,
    taskCompletedSubject: String
  )
  val live: RLayer[AvroSchemaRegistry & ConsumerSettings & AccountingService & Config, KafkaTaskListener] =
    ZLayer.scoped:
      for
        schemaRegistry    <- ZIO.service[AvroSchemaRegistry]
        consumerSettings  <- ZIO.service[ConsumerSettings]
        consumer          <- Consumer.make(consumerSettings)
        accountingService <- ZIO.service[AccountingService]
        config            <- ZIO.service[Config]
      yield KafkaTaskListener(schemaRegistry, consumer, accountingService, config)

final case class KafkaTaskListener(
  schemaRegistry: AvroSchemaRegistry,
  consumer: Consumer,
  accountingService: AccountingService,
  config: Config
) extends TaskListener:
  override def listenUpdates: UIO[Unit] =
    consumer
      .plainStream(Subscription.topics(config.taskLifecycleTopic), Serde.byteArray, Serde.byteArray)
      .runForeach: record =>
        val process =
          decode(record)
            .flatMap(accountingService.handleTaskUpdate)
            .catchAll: err =>
              ZIO.logError(s"Something went wrong when parsing update of a task: $err")

        ZIO.logInfo(s"processing $record") *> process *> record.offset.commit
      .orDie
      .tapErrorCause(ZIO.logErrorCause(s"Something really bad happened", _))
  end listenUpdates

  private def decode(record: CommittableRecord[_, Array[Byte]]): IO[IllegalArgumentException, TaskUpdate] =
    readSubjectHint(record)
      .flatMap:
        case Subject.Created    => decodeCreated(record)
        case Subject.Reassigned => decodeReassigned(record)
        case Subject.Completed  => decodeCompleted(record)
  end decode

  private val EVENT_VERSION_HEADER        = "event_version"
  private val SUBJECT_HINT_VERSION_HEADER = "subject_hint_version"
  private val SUBJECT_HINT_HEADER         = "subject_hint"

  private def decodeCreated(record: CommittableRecord[_, Array[Byte]]): IO[IllegalArgumentException, TaskUpdate] =
    findSchema(record = record, versionHeader = EVENT_VERSION_HEADER, subject = config.taskCreatedSubject)
      .flatMap: schema =>
        readOne(AvroInputStream.binary[TaskCreated].from(record.value).build(schema))(config.taskCreatedSubject)
      .map: t =>
        TaskUpdate(
          publicId = t.publicId,
          TaskUpdate.Update.Created(
            workerId = t.workerId,
            authorId = t.authorId,
            title = t.title,
            description = t.description
          ),
          updatedAt = t.updatedAt
        )

  private def decodeReassigned(record: CommittableRecord[_, Array[Byte]]): IO[IllegalArgumentException, TaskUpdate] =
    findSchema(record = record, versionHeader = EVENT_VERSION_HEADER, subject = config.taskReassignedSubject)
      .flatMap: schema =>
        readOne(AvroInputStream.binary[TaskReassigned].from(record.value).build(schema))(config.taskReassignedSubject)
      .map: t =>
        TaskUpdate(
          publicId = t.publicId,
          TaskUpdate.Update.Reassigned(t.workerId),
          updatedAt = t.updatedAt
        )

  private def decodeCompleted(record: CommittableRecord[_, Array[Byte]]): IO[IllegalArgumentException, TaskUpdate] =
    findSchema(record = record, versionHeader = EVENT_VERSION_HEADER, subject = config.taskCompletedSubject)
      .flatMap: schema =>
        readOne(AvroInputStream.binary[TaskCompleted].from(record.value).build(schema))(config.taskCreatedSubject)
      .map: t =>
        TaskUpdate(
          publicId = t.publicId,
          TaskUpdate.Update.Completed,
          updatedAt = t.updatedAt
        )

  private def readSubjectHint(record: CommittableRecord[_, Array[Byte]]): IO[IllegalArgumentException, Subject] =
    findSchema(
      record = record,
      versionHeader = SUBJECT_HINT_VERSION_HEADER,
      subject = config.taskLifecycleSubjectHeader
    )
      .flatMap: schema =>
        findHeader(record)(SUBJECT_HINT_HEADER) match
          case None => ZIO.fail(new IllegalArgumentException(s"Couldn't extract subject hint from headers"))
          case Some(hint) =>
            readOne(AvroInputStream.binary[Subject].from(hint.value).build(schema))(config.taskLifecycleSubjectHeader)
  end readSubjectHint

  private def findSchema(
    record: CommittableRecord[_, Array[Byte]],
    versionHeader: String,
    subject: String
  ): IO[IllegalArgumentException, Schema] =
    findHeader(record)(versionHeader) match
      case None =>
        ZIO.fail:
          new IllegalArgumentException(
            s"Couldn't extract schema version from ${record.record.headers} for $versionHeader"
          )
      case Some(versionBytes) =>
        val version = ByteBuffer.wrap(versionBytes.value).getInt()
        schemaRegistry
          .getForSubject(subject, version)
          .someOrFail(new IllegalArgumentException(s"Couldn't find schema $subject for version $version"))

  private def findHeader(record: CommittableRecord[_, Array[Byte]])(header: String): Option[Header] =
    Option(record.record.headers().lastHeader(header))

  private def readOne[A](is: => AvroInputStream[A])(subject: String): IO[IllegalArgumentException, A] =
    ZIO
      .attempt(is.iterator)
      .mapError(err => new IllegalArgumentException(s"Failed to read $subject", err))
      .flatMap: iterator =>
        if iterator.hasNext
        then ZIO.succeed(iterator.next())
        else ZIO.fail(new IllegalArgumentException(s"There is no data for $subject"))

  private given Decoder[TaskId]  = Decoder[String].map(TaskId(_))
  private given Decoder[PapugId] = Decoder[String].map(PapugId(_))

  private case class TaskCreated(
    @AvroName("public_id") publicId: TaskId,
    @AvroName("worker_id") workerId: PapugId,
    @AvroName("author_id") authorId: PapugId,
    @AvroName("title") title: String,
    @AvroName("description") description: Option[String],
    @AvroName("updated_at") updatedAt: Instant
  )

  private case class TaskReassigned(
    @AvroName("public_id") publicId: TaskId,
    @AvroName("worker_id") workerId: PapugId,
    @AvroName("updated_at") updatedAt: Instant
  )

  private case class TaskCompleted(
    @AvroName("public_id") publicId: TaskId,
    @AvroName("updated_at") updatedAt: Instant
  )

  private enum Subject:
    case Created
    case Reassigned
    case Completed

end KafkaTaskListener
