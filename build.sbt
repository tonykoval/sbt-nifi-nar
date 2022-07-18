enablePlugins(SbtPlugin, ScriptedPlugin, BuildInfoPlugin)

lazy val `2.10` = "2.10.7"
lazy val `2.11` = "2.11.12"
lazy val `2.12` = "2.12.15"
lazy val `2.13` = "2.13.7"

organization := "com.github.tonykoval"
name := "sbt-nifi-nar"

homepage := Some(url("https://github.com/tonykoval/sbt-nifi-nar"))
licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
publishMavenStyle := true
Test / publishArtifact := false
pomIncludeRepository := { _ => false }
publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)
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
  "org.apache.commons" % "commons-compress" % "1.21",
  "org.clapper" %% "classutil" % "1.5.1",
  "org.jsoup" % "jsoup" % "1.14.3"
)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "com.github.tonykoval.sbt.nifi"

val nifiVersion = "1.16.3"

libraryDependencies ++= Seq(
  "org.apache.nifi" % "nifi-api",
  "org.apache.nifi" % "nifi-documentation",
  "org.apache.nifi" % "nifi-framework-api",
  "org.apache.nifi" % "nifi-framework-nar-utils",
  "org.apache.nifi" % "nifi-commons",
  "org.apache.nifi" % "nifi-nar-utils"
).map(_ % nifiVersion)
