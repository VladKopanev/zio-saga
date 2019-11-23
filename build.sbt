import com.typesafe.sbt.SbtPgp.autoImportImpl.pgpSecretRing
import sbt.file

name := "zio-saga"

val mainScala = "2.12.10"
val allScala  = Seq("2.11.12", mainScala, "2.13.1")

inThisBuild(
  List(
    organization := "com.vladkopanev",
    homepage := Some(url("https://github.com/VladKopanev/zio-saga")),
    licenses := List("MIT License" -> url("https://opensource.org/licenses/MIT")),
    developers := List(
      Developer(
        "VladKopanev",
        "Vladislav Kopanev",
        "ivengo53@gmail.com",
        url("http://vladkopanev.com")
      )
    ),
    scmInfo := Some(
      ScmInfo(url("https://github.com/VladKopanev/zio-saga"), "scm:git:git@github.com/VladKopanev/zio-saga.git")
    ),
    pgpPublicRing := file("./travis/local.pubring.asc"),
    pgpSecretRing := file("./travis/local.secring.asc"),
    releaseEarlyWith := SonatypePublisher
  )
)

lazy val commonSettings = Seq(
  scalaVersion := mainScala,
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-explaintypes",
    "-Yrangepos",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-language:implicitConversions",
    "-unchecked",
    "-Xlint:_,-type-parameter-shadow",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused",
    "-Ywarn-value-discard"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 11)) =>
      Seq(
        "-Xfuture",
        "-Yno-adapted-args",
        "-Ypartial-unification",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit"
      )
    case Some((2, 12)) =>
      Seq(
        "-Xfuture",
        "-Xsource:2.13",
        "-Yno-adapted-args",
        "-Ypartial-unification",
        "-Ywarn-extra-implicit",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-opt-inline-from:<source>",
        "-opt-warnings",
        "-opt:l:inline",
        "-Ypartial-unification"
      )
    case _ => Nil
  }),
  resolvers ++= Seq(Resolver.sonatypeRepo("snapshots"), Resolver.sonatypeRepo("releases"))
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
    commonDeps
  )

val zioVersion           = "1.0.0-RC17"
val zioCatsVersion       = "2.0.0.0-RC8"
val scalatestVersion     = "3.0.8"
val http4sVersion        = "0.21.0-M5"
val log4CatsVersion      = "1.0.1"
val doobieVersion        = "0.8.6"
val circeVersion         = "0.12.3"
val psqlContainerVersion = "1.12.3"
val tcVersion            = "0.33.0"
val psqlDriverVersion    = "42.2.8"
val PSQLDriverVersion    = "42.2.8"
val ScalatestVersion     = "3.0.8"
val PsqlContVersion      = "1.12.3"
val h2database           = "1.4.200"

lazy val commonDeps =
  libraryDependencies ++= Seq(
    "dev.zio"        %% "zio"       % zioVersion,
    "org.scalatest"  %% "scalatest" % scalatestVersion % "test",
    "com.h2database" % "h2"         % h2Version
    // "com.dimafeng"       %% "testcontainers-scala" % tcVersion % "test",
    // "org.testcontainers" % "postgresql"            % PsqlContVersion,
    // "org.postgresql"     % "postgresql"            % PSQLDriverVersion
  )

lazy val examples = project
  .in(file("examples"))
  .settings(
    commonSettings,
    scalaVersion := mainScala,
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      "ch.qos.logback"    % "logback-classic"      % "1.2.3",
      "dev.zio"           %% "zio-interop-cats"    % zioCatsVersion,
      "io.chrisdavenport" %% "log4cats-core"       % log4CatsVersion,
      "io.chrisdavenport" %% "log4cats-slf4j"      % log4CatsVersion,
      "io.circe"          %% "circe-generic"       % circeVersion,
      "io.circe"          %% "circe-parser"        % circeVersion,
      "org.http4s"        %% "http4s-circe"        % http4sVersion,
      "org.http4s"        %% "http4s-dsl"          % http4sVersion,
      "org.http4s"        %% "http4s-blaze-server" % http4sVersion,
      "org.tpolecat"      %% "doobie-core"         % doobieVersion,
      "org.tpolecat"      %% "doobie-hikari"       % doobieVersion,
      "org.tpolecat"      %% "doobie-postgres"     % doobieVersion,
      // compilerPlugin("org.scalamacros"  %% "paradise"           % "2.1.0"),
      compilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.0" cross CrossVersion.full),
      compilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
    )
  )
  .dependsOn(core % "compile->compile")

addCommandAlias("rel", "reload")
//addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fmt", "all scalafmtSbt")
