name := "zio-saga"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-zio" % "1.0-RC4",
  "org.scalactic" %% "scalactic" % "3.0.5",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

scalacOptions in Compile += "-Ypartial-unification"