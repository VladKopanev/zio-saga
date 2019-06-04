name := "zio-saga"

lazy val commonSettings = Seq(
  organization := "com.vladkopanev",
  version := "0.1.0",
  scalaVersion := "2.12.8"
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

lazy val examples = project
  .in(file("examples"))
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-core"      % "0.7.0",
      "org.tpolecat" %% "doobie-hikari"    % "0.7.0",
      "org.tpolecat" %% "doobie-postgres"  % "0.7.0",
      "org.scalaz"    %% "scalaz-zio-interop-cats" % "1.0-RC5"
    )
  )
  .dependsOn(core % "test->test;compile->compile")
