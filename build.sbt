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
    commonSettings
  )
  .dependsOn(core % "test->test;compile->compile")
