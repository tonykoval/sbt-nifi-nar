addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.0")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.12")
addSbtPlugin("com.dwijnand" % "sbt-travisci" % "1.2.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.8")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
