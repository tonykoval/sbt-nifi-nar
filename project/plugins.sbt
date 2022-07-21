addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.0")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.4")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.33")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
