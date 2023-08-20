package com.home.tasks.adapters

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.Instant

import scala.jdk.CollectionConverters.*

import com.home.avro.schema.AvroSchemaRegistry
import com.home.tasks.adapters.KafkaTaskPublisher.Config
import com.home.tasks.model.*
import com.home.tasks.service.TaskPublisher
import com.sksamuel.avro4s.{ AvroName, AvroOutputStream, Encoder }
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader

import zio.kafka.producer.{ Producer, ProducerSettings }
import zio.kafka.serde.Serde
import zio.{ Task as _, * }

object KafkaTaskPublisher:
  case class Config(
    taskLifecycleTopic: String,
    taskLifecycleSubjectHeader: String,
    taskCreatedSubject: String,
    taskReassignedSubject: String,
    taskCompletedSubject: String
  )

  val live: RLayer[AvroSchemaRegistry & ProducerSettings & Config, KafkaTaskPublisher] =
    ZLayer.scoped:
      for
        producerSettings <- ZIO.service[ProducerSettings]
        producer         <- Producer.make(producerSettings)
        schemaRegistry   <- ZIO.service[AvroSchemaRegistry]
        config           <- ZIO.service[Config]
      yield KafkaTaskPublisher(schemaRegistry, producer, config)

final case class KafkaTaskPublisher(schemaRegistry: AvroSchemaRegistry, producer: Producer, config: Config)
    extends TaskPublisher:
  override def publish(tasks: Chunk[TaskUpdate]): UIO[Unit] =
    ZIO
      .foreach(tasks): task =>
        val taskUpdate =
          task.update match
            case TaskUpdate.Update.Created(workerId, authorId, title, description) =>
              val event   = TaskCreated(task.publicId, workerId, authorId, title, description, task.updatedAt)
              val subject = Subject.Created
              encode(event, subject).map(_ -> subject)
            case TaskUpdate.Update.Reassigned(workerId) =>
              val event   = TaskReassigned(task.publicId, workerId, task.updatedAt)
              val subject = Subject.Reassigned
              encode(event, subject).map(_ -> subject)
            case TaskUpdate.Update.Completed =>
              val event   = TaskCompleted(task.publicId, task.updatedAt)
              val subject = Subject.Completed
              encode(event, subject).map(_ -> subject)

        taskUpdate
          .flatMap: (event, subject) =>
            subjectHintHeader(subject).flatMap:
              case None       => ZIO.dieMessage("Uh oh! Subject hint header not found!")
              case Some(hint) => ZIO.succeed(event, hint)
          .flatMap:
            case (Some(encoded), subjectHint) =>
              val headers = List(EVENT_VERSION_HEADER, subjectHint, SUBJECT_HINT_VERSION_HEADER).asJava
              ZIO.some:
                new ProducerRecord(config.taskLifecycleTopic, null, TaskId.unwrap(task.publicId), encoded, headers)
            case (_, subject) =>
              ZIO.logWarning(s"Uh oh! Schema $subject not found for task: $task! Skipping...") *> ZIO.none
      .flatMap: records =>
        producer.produceChunk(records.flatten, Serde.string, Serde.byteArray).orDie.unit
      .tapErrorCause(ec => ZIO.logErrorCause("Uh oh! Failed to publish task updates!", ec))

  end publish

  private def encode[T: Encoder](task: T, subject: Subject): UIO[Option[Array[Byte]]] =
    val subjectStr =
      subject match
        case Subject.Created    => config.taskCreatedSubject
        case Subject.Reassigned => config.taskReassignedSubject
        case Subject.Completed  => config.taskCompletedSubject
    schemaRegistry
      .getForSubject(subjectStr, 1)
      .flatMap:
        case None => ZIO.none
        case Some(schema) =>
          ZIO
            .succeed:
              val os  = new ByteArrayOutputStream()
              val aos = AvroOutputStream.binary(schema, Encoder[T]).to(os).build()
              aos.write(task)
              aos.close()
              Some(os.toByteArray)
  end encode

  private val EVENT_VERSION = 1
  private val EVENT_VERSION_HEADER: Header =
    val bb = ByteBuffer.allocate(4)
    bb.putInt(EVENT_VERSION)
    new RecordHeader("event_version", bb.array())

  private val SUBJECT_HINT_VERSION = 1
  private val SUBJECT_HINT_VERSION_HEADER: Header =
    val bb = ByteBuffer.allocate(4)
    bb.putInt(SUBJECT_HINT_VERSION)
    new RecordHeader("subject_hint_version", bb.array())

  private def subjectHintHeader(subject: Subject): UIO[Option[Header]] =
    schemaRegistry
      .getForSubject(config.taskLifecycleSubjectHeader, SUBJECT_HINT_VERSION)
      .flatMap:
        case None => ZIO.none
        case Some(schema) =>
          ZIO
            .succeed:
              val os  = new ByteArrayOutputStream()
              val aos = AvroOutputStream.binary(schema, Encoder[Subject]).to(os).build()
              aos.write(subject)
              aos.close()
              Some(new RecordHeader("subject_hint", os.toByteArray))

  private given Encoder[TaskId]  = Encoder[String].contramap(TaskId.unwrap)
  private given Encoder[PapugId] = Encoder[String].contramap(PapugId.unwrap)

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

end KafkaTaskPublisher
