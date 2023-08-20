package com.home.tasks.service

import java.util.UUID

import com.home.tasks.model.{ Task, * }
import com.home.tasks.repo.TaskRepo
import com.home.tasks.service.TaskService.Error

import zio.*

trait TaskService:
  def createTask(request: CreateTaskRequest): ZIO[RequestContext, TaskService.Error, CreateTaskResponse]
  def reassignTasks: ZIO[RequestContext, Error.Unauthorized, Unit]
  def completeTask(id: TaskId): ZIO[RequestContext, Error, Unit]
  def getTasks: ZIO[RequestContext, TaskService.Error.NotFound, Chunk[Task]]

object TaskService:
  enum Error:
    case BadRequest(message: String)
    case Unauthorized(message: String)
    case NotFound(message: String)

object TaskServiceLive:
  val live: URLayer[TaskRepo & PapugService & TaskPublisher, TaskServiceLive] =
    ZLayer.fromFunction(TaskServiceLive.apply _)

final case class TaskServiceLive(taskRepo: TaskRepo, papugService: PapugService, taskPublisher: TaskPublisher)
    extends TaskService:
  override def createTask(request: CreateTaskRequest): ZIO[RequestContext, TaskService.Error, CreateTaskResponse] =
    for
      _       <- ZIO.logInfo(s"Creating task: $request")
      context <- ZIO.service[RequestContext]
      now     <- Clock.instant
      author  <- papugByEmail(context.papug)
      workers <- findAllWorkers
      worker  <- Random.nextIntBounded(workers.size).map(workers(_))

      publicId = TaskId(UUID.randomUUID().toString)
      task = Task(
        publicId = publicId,
        workerId = worker.id,
        authorId = author.id,
        title = request.title,
        description = request.description,
        status = Task.Status.InProgress,
        updatedAt = now
      )
      _ <- upsert(Chunk.single(task))
    yield CreateTaskResponse(task.publicId, task.workerId)

  override def reassignTasks: ZIO[RequestContext, Error.Unauthorized, Unit] =
    for
      context <- ZIO.service[RequestContext]
      _ <- ZIO.unless(isImportantPapug(context.roles)) {
        ZIO.fail(Error.Unauthorized(s"User ${context.papug} is not authorized to reassign tasks"): Error.Unauthorized)
      }
      _ <- ZIO.logInfo(s"Reassigning tasks")

      papugs <- papugService.findAll.filterNot(papug => isImportantPapug(papug.roles)).runCollect
      tasks  <- taskRepo.findAllWithStatus(Task.Status.InProgress).runCollect
      now    <- Clock.instant

      reassigned <- ZIO.foreachPar(tasks) { task =>
        Random.nextIntBounded(papugs.size).map { index =>
          task.copy(workerId = papugs(index).id, updatedAt = now)
        }
      }
      _ <- upsert(reassigned)
    yield ()

  override def completeTask(id: TaskId): ZIO[RequestContext, Error, Unit] =
    for
      context <- ZIO.service[RequestContext]

      task  <- taskRepo.findById(id).someOrFail(Error.NotFound(s"Task $id not found"))
      papug <- papugByEmail(context.papug)
      _ <- ZIO.unless(task.workerId == papug.id) {
        ZIO.fail(Error.Unauthorized(s"User ${context.papug} is not authorized to complete task $id"))
      }
      _ <- ZIO.when(task.status == Task.Status.Done) {
        ZIO.fail(Error.BadRequest(s"The task $id is already completed"))
      }

      _ <- ZIO.logInfo(s"Completing task: $id")
      _ <- upsert(Chunk.single(task.copy(status = Task.Status.Done)))
    yield ()

  override def getTasks: ZIO[RequestContext, TaskService.Error.NotFound, Chunk[Task]] =
    for
      context <- ZIO.service[RequestContext]
      papug   <- papugByEmail(context.papug)
      _       <- ZIO.logInfo(s"Getting tasks for papug: $papug")
      tasks   <- taskRepo.findAllForPapug(papug.id).runCollect
    yield tasks

  private def findAllWorkers =
    papugService.findAll.filterNot(papug => isImportantPapug(papug.roles)).runCollect

  private def isImportantPapug(roles: Set[Role]): Boolean =
    roles.contains(Role.ADMIN) || roles.contains(Role.MANAGER)

  private def papugByEmail(email: Email): IO[Error.NotFound, Papug] =
    papugService.findByEmail(email).someOrFail(Error.NotFound(s"Papug $email not found"): Error.NotFound)

  private def upsert(tasks: Chunk[Task]): UIO[Unit] =
    taskRepo.upsert(tasks) *> taskPublisher.publish(tasks)

end TaskServiceLive
