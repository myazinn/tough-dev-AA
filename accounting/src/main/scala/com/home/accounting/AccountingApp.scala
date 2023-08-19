package com.home.accounting

import com.home.accounting.adapters.*

import zio.*
import zio.http.Server

object AccountingApp extends ZIOAppDefault:

  override def run: ZIO[Scope, Any, Any] =
    val listenPapugUpdates = ZIO.serviceWithZIO[PapugListener](_.listenUpdates)
    val listenTaskUpdates  = ZIO.serviceWithZIO[TaskListener](_.listenUpdates)

    val runHTTPServer =
      for
        routes <- ZIO.serviceWith[AccountingRoutes](_.routes)
        _      <- Server.serve(routes.toHttpApp)
      yield ()

    val app = runHTTPServer.raceAll(Chunk(listenPapugUpdates, listenTaskUpdates))

    app.provide(
      AccountingRoutesLive.live,
      KafkaPapugListener.live,
      KafkaTaskListener.live,
      Server.defaultWithPort(8001)
    )
