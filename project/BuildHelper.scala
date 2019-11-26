import sbt._
import Keys._
import com.typesafe.sbt.SbtPgp.autoImportImpl._ //{pgpSecretRing, pgpPublicRing, releaseEarlyWith, SonatypePublisher}

object BuildHelper {
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
      pgpSecretRing := file("./travis/local.secring.asc")
      // releaseEarlyWith := SonatypePublisher
    )
  )

  val zioVersion           = "1.0.0-RC17"
  val zioCatsVersion       = "2.0.0.0-RC9"
  val scalatestVersion     = "3.0.8"
  val http4sVersion        = "0.21.0-M5"
  val log4CatsVersion      = "1.0.1"
  val doobieVersion        = "0.8.6"
  val circeVersion         = "0.12.3"
  val psqlContainerVersion = "1.12.3"
  val tcVersion            = "0.33.0"
  val psqlDriverVersion    = "42.2.8"
  val ssqlContVersion      = "1.12.3"
  val h2Version            = "1.4.200"
  val logbackVersion       = "1.2.3"

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
}
