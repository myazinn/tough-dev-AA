package com.home.tasks.adapters

import com.home.tasks.model.*
import com.home.tasks.service.TaskService
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*

import zio.http.Method.*
import zio.http.*
import zio.http.codec.PathCodec.string
import zio.{ Task as _, * }

trait TaskServiceRoutes:
  def routes: Routes[Any, Nothing]

object TaskServiceRoutesLive:
  val live: URLayer[TaskService, TaskServiceRoutesLive] = ZLayer.fromFunction(TaskServiceRoutesLive.apply _)

final case class TaskServiceRoutesLive(taskService: TaskService) extends TaskServiceRoutes:

  val routes: Routes[Any, Nothing] =
    val getTasks =
      GET / "tasks" -> handler { (req: Request) =>
        taskService.getTasks
          .provide(requestContext(req))
          .fold(errorToResponse, jsonResponse(_))
      }

    val createTask =
      POST / "tasks" -> handler { (req: Request) =>
        withDecodedBody(req): (request: CreateTaskRequest) =>
          taskService
            .createTask(request)
            .provide(requestContext(req))
            .fold(errorToResponse, jsonResponse(_))
      }

    val reassignTasks =
      PUT / "tasks" -> handler { (req: Request) =>
        taskService.reassignTasks
          .provide(requestContext(req))
          .fold(errorToResponse, _ => jsonResponse("Tasks reassigned!"))
      }

    val completeTask =
      PUT / "tasks" / string("taskId") -> handler { (taskId: String, req: Request) =>
        taskService
          .completeTask(TaskId(taskId))
          .provide(requestContext(req))
          .fold(errorToResponse, _ => jsonResponse(s"$taskId is done!"))
      }

    Routes(getTasks, createTask, reassignTasks, completeTask)
      .transform: h =>
        h.catchAllCause: cause =>
          handler:
            ZIO.logErrorCause(cause).as(Response.internalServerError("Something went wrong!"))

  end routes

  private given Encoder[PapugId]     = Encoder.encodeString.contramap(id => PapugId.unwrap(id))
  private given Encoder[TaskId]      = Encoder.encodeString.contramap(id => TaskId.unwrap(id))
  private given Encoder[Email]       = Encoder.encodeString.contramap(id => Email.unwrap(id))
  private given Encoder[Task.Status] = Encoder.encodeString.contramap(_.toString)

  private def withDecodedBody[R, E, Entity: Decoder](
    request: Request
  )(handle: Entity => ZIO[R, E, Response]): ZIO[R, E, Response] =
    request.body.asString
      .flatMap(body => ZIO.fromEither(io.circe.parser.parse(body).flatMap(_.as[Entity])))
      .foldZIO(err => ZIO.succeed(Response.text(err.getMessage).copy(Status.BadRequest)), handle)

  private def errorToResponse(error: TaskService.Error) =
    error match
      case nf: TaskService.Error.NotFound     => jsonResponse(nf, Status.NotFound)
      case ua: TaskService.Error.Unauthorized => jsonResponse(ua, Status.Forbidden)

  private def jsonResponse[Entity: Encoder](entity: Entity, status: Status = Status.Ok): Response =
    Response.json(entity.asJson.noSpaces).copy(status = status)

  // don't want to mess with middleware and token, going the easy way
  private def requestContext(request: Request): Layer[TaskService.Error, RequestContext] = {
    val email =
      ZIO
        .fromOption(request.headers.find(_.headerName == "X-Forwarded-Email").map(h => Email(h.renderedValue)))
        .orElseFail(TaskService.Error.Unauthorized("email not found in headers"))

    val roles =
      request.headers
        .find(_.headerName == "X-Forwarded-Groups")
        .toSet
        .flatMap(_.renderedValue.split(","))
        .map(_.split(":").last)
        .map(Role(_))

    ZLayer.fromZIO:
      email.map(RequestContext(_, roles))
  }

end TaskServiceRoutesLive
