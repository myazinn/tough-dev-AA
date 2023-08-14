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
    .aggregate(`task-tracker`, auth, `keycloak-to-kafka`)
    .settings(
      name := "tough-dev-AA"
    )

lazy val `task-tracker` =
  (project in file("task-tracker"))
    .enablePlugins(ScalafixPlugin, JavaAppPackaging, DockerPlugin)
    .settings(
      name := "task-tracker",
      libraryDependencies ++= zioDeps ++ kafkaDeps ++ zioHTTPDeps ++ circeDeps ++ logDeps,
      Compile / mainClass  := Some("com.home.tasks.TaskTrackerApp"),
      Docker / packageName := "async_architecture/task-tracker",
      dockerBaseImage      := "eclipse-temurin:17"
    )

lazy val auth =
  (project in file("auth"))
    .enablePlugins(ScalafixPlugin, JavaAppPackaging)
    .settings(
      name := "auth",
      libraryDependencies ++= zioDeps ++ kafkaDeps ++ circeDeps ++ logDeps ++ postgresDeps,
      Compile / mainClass  := Some("com.home.keycloak.acl.KeycloakACLApp"),
      Docker / packageName := "async_architecture/auth",
      dockerBaseImage      := "eclipse-temurin:17"
    )

lazy val `keycloak-to-kafka` =
  (project in file("keycloak-to-kafka"))
    .enablePlugins(ScalafixPlugin)
    .settings(
      name := "keycloak-to-kafka",
      libraryDependencies ++= zioDeps ++ kafkaDeps ++ circeDeps ++ logDeps ++ keycloakDeps,
      assemblyMergeStrategy := {
        case PathList("module-info.class")         => MergeStrategy.discard
        case PathList("META-INF", _*)              => MergeStrategy.first
        case x if x.endsWith("/module-info.class") => MergeStrategy.discard
        case x =>
          val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
          oldStrategy(x)
      }
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
lazy val keycloakVersion = "22.0.1"
lazy val doobieVersion   = "1.0.0-RC4"
lazy val zioCatsVersion  = "23.0.03"

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

lazy val keycloakDeps = Seq(
  "org.keycloak" % "keycloak-server-spi",
  "org.keycloak" % "keycloak-server-spi-private",
  "org.keycloak" % "keycloak-services"
).map(_ % keycloakVersion % Provided)

lazy val postgresDeps = {
  val doobie = Seq(
    "org.tpolecat" %% "doobie-core",
    "org.tpolecat" %% "doobie-hikari",
    "org.tpolecat" %% "doobie-postgres"
  ).map(_ % doobieVersion)

  val interop = Seq(
    "dev.zio" %% "zio-interop-cats" % zioCatsVersion
  )

  doobie ++ interop
}
