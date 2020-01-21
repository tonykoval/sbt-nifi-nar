enablePlugins(SbtPlugin, ScriptedPlugin, BuildInfoPlugin)

lazy val `2.10` = "2.10.7"
lazy val `2.11` = "2.11.12"
lazy val `2.12` = "2.12.10"
lazy val `2.13` = "2.13.1"

organization := "com.github.tonykoval"
name := "sbt-nifi-nar"

homepage := Some(url("https://github.com/tonykoval/sbt-nifi-nar"))
licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ => false }
publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}
scmInfo := Some(
  ScmInfo(
    url("https://github.com/tonykoval/sbt-nifi-nar"),
    "scm:git:git@github.com/tonykoval/sbt-nifi-nar.git"
  )
)

developers := List(
  Developer("tonykoval", "Anton Koval", "tony@tonykoval.com", url("http://tonykoval.com"))
)

sbtPlugin := true

crossScalaVersions := Seq(`2.10`, `2.11`, `2.12`, `2.13`)

scalaVersion := `2.12`

scriptedBufferLog := false
scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value, "-Dplugin.name=" + name.value)
}

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-compress" % "1.19",
  "org.clapper" %% "classutil" % "1.5.1",
  "org.jsoup" % "jsoup" % "1.11.3"
)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

val nifiVersion = "1.10.0"

libraryDependencies ++= Seq(
  "org.apache.nifi" % "nifi-api",
  "org.apache.nifi" % "nifi-documentation",
  "org.apache.nifi" % "nifi-framework-api",
  "org.apache.nifi" % "nifi-framework-nar-utils",
  "org.apache.nifi" % "nifi-commons",
).map(_ % nifiVersion)
