import com.typesafe.sbt.SbtPgp.autoImportImpl.pgpSecretRing
import sbt.file

name := "zio-saga"

val mainScala = "2.13.11"
val allScala = Seq("2.12.17", mainScala, "3.1.3")

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
  resolvers ++= Resolver.sonatypeOssRepos("snapshots") ++ Resolver.sonatypeOssRepos("releases")
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
      "dev.zio" %% "zio"          % Versions.Zio,
      "dev.zio" %% "zio-test"     % Versions.Zio % "test",
      "dev.zio" %% "zio-test-sbt" % Versions.Zio % "test"
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val examples = project
  .in(file("examples"))
  .settings(
    commonSettings,
    scalaVersion := mainScala,
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic"     % "1.2.10",
      "dev.zio"       %% "zio-interop-cats"    % "3.2.9.0",
      "org.typelevel" %% "log4cats-core"       % Versions.Log4Cats,
      "org.typelevel" %% "log4cats-slf4j"      % Versions.Log4Cats,
      "io.circe"      %% "circe-generic"       % Versions.Circe,
      "io.circe"      %% "circe-parser"        % Versions.Circe,
      "org.http4s"    %% "http4s-circe"        % Versions.Http4s,
      "org.http4s"    %% "http4s-dsl"          % Versions.Http4s,
      "org.http4s"    %% "http4s-blaze-server" % Versions.Http4s,
      "org.tpolecat"  %% "doobie-core"         % Versions.Doobie,
      "org.tpolecat"  %% "doobie-hikari"       % Versions.Doobie,
      "org.tpolecat"  %% "doobie-postgres"     % Versions.Doobie,
      // compilerPlugin("org.scalamacros"  %% "paradise"           % "2.1.0"),
      compilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.2" cross CrossVersion.full),
      compilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
    )
  )
  .dependsOn(core % "compile->compile")
