import com.typesafe.sbt.SbtPgp.autoImportImpl.pgpSecretRing
import sbt.file

name := "zio-saga"

val mainScala = "2.13.6"
val allScala = Seq("2.12.15", mainScala, "3.0.2")

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
    case Some((3, _)) =>
      Seq("-Ykind-projector", "-unchecked")
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
  resolvers ++= Seq(Resolver.sonatypeRepo("snapshots"), Resolver.sonatypeRepo("releases"))
)

lazy val root = project
  .in(file("."))
  .aggregate(core)

lazy val core = project
  .in(file("core"))
  .settings(
    commonSettings,
    name := "zio-saga-core",
    crossScalaVersions := allScala,
    libraryDependencies ++= Seq(
      "dev.zio"       %% "zio"          % "1.0.12",
      "dev.zio"       %% "zio-test"     % "1.0.12" % "test",
      "dev.zio"       %% "zio-test-sbt" % "1.0.12" % "test"
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

val http4sVersion   = "0.21.11"
val log4CatsVersion = "1.1.1"
val doobieVersion   = "0.9.0"
val circeVersion    = "0.13.0"

lazy val examples = project
  .in(file("examples"))
  .settings(
    commonSettings,
    scalaVersion := mainScala,
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      "ch.qos.logback"    % "logback-classic"          % "1.2.3",
      "dev.zio"           %% "zio-interop-cats"        % "2.0.0.0-RC14",
      "io.chrisdavenport" %% "log4cats-core"           % log4CatsVersion,
      "io.chrisdavenport" %% "log4cats-slf4j"          % log4CatsVersion,
      "io.circe"          %% "circe-generic"           % circeVersion,
      "io.circe"          %% "circe-parser"            % circeVersion,
      "org.http4s"        %% "http4s-circe"            % http4sVersion,
      "org.http4s"        %% "http4s-dsl"              % http4sVersion,
      "org.http4s"        %% "http4s-blaze-server"     % http4sVersion,
      "org.tpolecat"      %% "doobie-core"             % doobieVersion,
      "org.tpolecat"      %% "doobie-hikari"           % doobieVersion,
      "org.tpolecat"      %% "doobie-postgres"         % doobieVersion,
     // compilerPlugin("org.scalamacros"  %% "paradise"           % "2.1.0"),
      compilerPlugin("org.typelevel"    %% "kind-projector"     % "0.11.2" cross CrossVersion.full),
      compilerPlugin("com.olegpy"       %% "better-monadic-for" % "0.3.1")
    )
  )
  .dependsOn(core % "compile->compile")
