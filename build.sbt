name := "zio-saga"


lazy val commonSettings = Seq(
  organization := "org.zio",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.12.8"
)

lazy val core = project
  .in(file("core"))
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.scalaz"    %% "scalaz-zio" % "1.0-RC5",
      "org.scalactic" %% "scalactic"  % "3.0.5",
      "org.scalatest" %% "scalatest"  % "3.0.5" % "test"
    )
  )

lazy val interopCats = project
  .in(file("interop-cats"))
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.scalaz"    %% "scalaz-zio" % "1.0-RC4",
      "org.typelevel" %% "cats-effect" % "1.3.0" % Optional
    )
  )
  .dependsOn(core % "test->test;compile->compile")

lazy val root = project
  .in(file("."))
  .aggregate(core, interopCats)