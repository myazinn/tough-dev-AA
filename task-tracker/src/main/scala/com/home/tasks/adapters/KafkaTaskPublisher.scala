package com.home.tasks.adapters

import java.io.ByteArrayOutputStream
import java.time.Instant

import com.home.avro.schema.AvroSchemaRegistry
import com.home.tasks.adapters.KafkaTaskPublisher.Config
import com.home.tasks.model.*
import com.home.tasks.service.TaskPublisher
import com.sksamuel.avro4s.{ AvroName, AvroOutputStream, Encoder }

import zio.{ Task as _, * }

object KafkaTaskPublisher:
  case class Config(taskCreatedSubject: String, taskReassignedSubject: String, taskCompletedSubject: String)
  val live: URLayer[AvroSchemaRegistry & Config, KafkaTaskPublisher] = ZLayer.fromFunction(KafkaTaskPublisher.apply _)

final case class KafkaTaskPublisher(schemaRegistry: AvroSchemaRegistry, config: Config) extends TaskPublisher:
  override def publish(tasks: Chunk[TaskUpdate]): UIO[Unit] =
    ZIO.foreachDiscard(tasks): task =>
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

      taskUpdate.flatMap:
        case (Some(_), subject) => ZIO.logInfo(s"Published task: $task with subject: $subject")
        case (None, subject)    => ZIO.logWarning(s"Uh oh! Schema $subject not found for task: $task! Skipping...")

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
