package com.home.accounting.adapters

import zio.*
import zio.http.Method.*
import zio.http.*

trait AccountingRoutes:
  def routes: Routes[Any, Nothing]

object AccountingRoutesLive:
  val live: ULayer[AccountingRoutesLive] = ZLayer.succeed(AccountingRoutesLive())

final case class AccountingRoutesLive() extends AccountingRoutes:
  val routes: Routes[Any, Nothing] =
    val dummy =
      GET / "accouting" -> handler { (req: Request) =>
        Response.text("Hello from accounting!")
      }

    Routes(dummy)
      .transform: h =>
        h.catchAllCause: cause =>
          handler:
            ZIO.logErrorCause(cause).as(Response.internalServerError("Something went wrong!"))

  end routes
