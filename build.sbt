inThisBuild(
  List(
    organization      := "com.home",
    scalaVersion      := "3.3.0",
    version           := "0.1.0",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val root =
  (project in file("."))
    .aggregate(`task-tracker`)
    .settings(
      name := "tough-dev-AA"
    )

lazy val `task-tracker` =
  (project in file("task-tracker"))
    .enablePlugins(ScalafixPlugin, JavaAppPackaging, DockerPlugin)
    .settings(
      name := "task-tracker",
      libraryDependencies ++= zioDeps ++ kafkaDeps ++ zioHTTPDeps ++ circeDeps ++ logDeps
    )

ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "utf-8",
  "-language:higherKinds", // Allow higher-kinded types
  "-new-syntax",           // Require `then` and `do` in control expressions
  "-unchecked",            // Enable additional warnings where generated code depends on assumptions
  "-deprecation",          // Emit warning and location for usages of deprecated APIs
  "-Xfatal-warnings",      // Fail the compilation if there are any warnings
  "-Wunused:all",          // Enable warnings for unused imports, params, and privates
  "-Wconf:any:error"
)

lazy val zioVersion      = "2.0.15"
lazy val zioKafkaVersion = "2.4.2"
lazy val zioHTTPVersion  = "3.0.0-RC2"
lazy val circeVersion    = "0.14.5"
lazy val logbackVersion  = "1.4.11"

lazy val zioDeps = Seq(
  "dev.zio" %% "zio",
  "dev.zio" %% "zio-streams"
).map(_ % zioVersion)

lazy val kafkaDeps = Seq(
  "dev.zio" %% "zio-kafka"
).map(_ % zioKafkaVersion)

lazy val zioHTTPDeps = Seq(
  "dev.zio" %% "zio-http"
).map(_ % zioHTTPVersion)

lazy val circeDeps = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

lazy val logDeps = Seq(
  "ch.qos.logback" % "logback-classic"
).map(_ % logbackVersion)
