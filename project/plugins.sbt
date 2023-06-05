addSbtPlugin("org.scalameta" % "sbt-scalafmt"      % "2.3.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage"     % "2.0.8")
addSbtPlugin("ch.epfl.scala" % "sbt-release-early" % "2.1.1")

// from https://github.com/sbt/sbt/issues/6745#issuecomment-1442315151
libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
