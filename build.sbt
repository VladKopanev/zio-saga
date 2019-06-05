name := "zio-saga"

lazy val commonSettings = Seq(
  organization := "com.vladkopanev",
  version := "0.1.0",
  scalaVersion := "2.12.8",
  scalacOptions ++= Seq("-Ypartial-unification"),
  resolvers ++= Seq(Resolver.sonatypeRepo("snapshots"), Resolver.sonatypeRepo("releases"))
)

lazy val core = project
  .in(file("core"))
  .settings(
    commonSettings,
    crossScalaVersions := Seq("2.12.8", "2.11.12"),
    libraryDependencies ++= Seq(
      "org.scalaz"    %% "scalaz-zio" % "1.0-RC5",
      "org.scalactic" %% "scalactic"  % "3.0.5",
      "org.scalatest" %% "scalatest"  % "3.0.5" % "test"
    )
  )

val http4sVersion   = "0.20.1"
val log4CatsVersion = "0.3.0"
val doobieVersion   = "0.7.0"

lazy val examples = project
  .in(file("examples"))
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "io.chrisdavenport" %% "log4cats-core"           % log4CatsVersion,
      "io.chrisdavenport" %% "log4cats-slf4j"          % log4CatsVersion,
      "io.circe"          %% "circe-generic"           % "0.11.1",
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
  .dependsOn(core % "test->test;compile->compile")
