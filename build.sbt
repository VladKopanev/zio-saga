import sbt.file
import BuildHelper._

name := "zio-saga"

lazy val common =
  libraryDependencies ++= Seq(
    "dev.zio"       %% "zio"       % zioVersion,
    "org.scalatest" %% "scalatest" % scalatestVersion % "test"
  )

lazy val doobie =
  libraryDependencies ++= Seq(
    "org.tpolecat" %% "doobie-core"     % doobieVersion,
    "org.tpolecat" %% "doobie-hikari"   % doobieVersion,
    "org.tpolecat" %% "doobie-postgres" % doobieVersion
  )

lazy val psql =
  libraryDependencies ++= Seq(
    "com.dimafeng"       %% "testcontainers-scala" % tcVersion % "test",
    "org.testcontainers" % "postgresql"            % psqlContainerVersion,
    "org.postgresql"     % "postgresql"            % psqlDriverVersion
    // "com.h2database"     % "h2"                    % h2Version
  )

lazy val root = project
  .in(file("."))
  .dependsOn(examples)
  .aggregate(core)

lazy val core = project
  .in(file("core"))
  .settings(
    commonSettings,
    name := "zio-saga-core",
    crossScalaVersions := allScala,
    common,
    doobie,
    psql
  )

lazy val examples = project
  .in(file("examples"))
  .settings(
    commonSettings,
    scalaVersion := mainScala,
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      "ch.qos.logback"    % "logback-classic"      % logbackVersion,
      "dev.zio"           %% "zio-interop-cats"    % zioCatsVersion,
      "io.chrisdavenport" %% "log4cats-core"       % log4CatsVersion,
      "io.chrisdavenport" %% "log4cats-slf4j"      % log4CatsVersion,
      "io.circe"          %% "circe-generic"       % circeVersion,
      "io.circe"          %% "circe-parser"        % circeVersion,
      "org.http4s"        %% "http4s-circe"        % http4sVersion,
      "org.http4s"        %% "http4s-dsl"          % http4sVersion,
      "org.http4s"        %% "http4s-blaze-server" % http4sVersion,
      // compilerPlugin("org.scalamacros"  %% "paradise"           % "2.1.0"),
      compilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.0" cross CrossVersion.full),
      compilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
    ),
    doobie
  )
  .dependsOn(core % "compile->compile")

addCommandAlias("rel", "reload")
//addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fmt", "all scalafmtSbt")
