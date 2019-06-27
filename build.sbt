import com.typesafe.sbt.SbtPgp.autoImportImpl.pgpSecretRing
import sbt.file

name := "zio-saga"

val mainScala = "2.12.8"
val allScala  = Seq("2.11.12", mainScala)

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
    "-Xfuture",
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
        "-Yno-adapted-args",
        "-Ypartial-unification",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit"
      )
    case Some((2, 12)) =>
      Seq(
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
        "-opt:l:inline"
      )
    case _ => Nil
  }),
  scalacOptions ++= Seq("-Ypartial-unification"),
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
    coverageEnabled := true,
    crossScalaVersions := allScala,
    libraryDependencies ++= Seq(
      "org.scalaz"    %% "scalaz-zio" % "1.0-RC5",
      "org.scalatest" %% "scalatest"  % "3.0.5" % "test"
    )
  )

val http4sVersion   = "0.20.1"
val log4CatsVersion = "0.3.0"
val doobieVersion   = "0.7.0"
val circeVersion    = "0.11.1"

lazy val examples = project
  .in(file("examples"))
  .settings(
    commonSettings,
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      "ch.qos.logback"    % "logback-classic"          % "1.2.3",
      "io.chrisdavenport" %% "log4cats-core"           % log4CatsVersion,
      "io.chrisdavenport" %% "log4cats-slf4j"          % log4CatsVersion,
      "io.circe"          %% "circe-generic"           % circeVersion,
      "io.circe"          %% "circe-parser"            % circeVersion,
      "org.http4s"        %% "http4s-circe"            % http4sVersion,
      "org.http4s"        %% "http4s-dsl"              % http4sVersion,
      "org.http4s"        %% "http4s-blaze-server"     % http4sVersion,
      "org.scalaz"        %% "scalaz-zio-interop-cats" % "1.0-RC5",
      "org.tpolecat"      %% "doobie-core"             % doobieVersion,
      "org.tpolecat"      %% "doobie-hikari"           % doobieVersion,
      "org.tpolecat"      %% "doobie-postgres"         % doobieVersion,
      compilerPlugin("org.scalamacros" % "paradise"            % "2.1.0" cross CrossVersion.full),
      compilerPlugin("org.spire-math"  %% "kind-projector"     % "0.9.9"),
      compilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.3.0-M4")
    )
  )
  .dependsOn(core % "compile->compile")
