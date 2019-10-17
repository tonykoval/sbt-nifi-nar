enablePlugins(SbtPlugin, ScriptedPlugin, BuildInfoPlugin)

organization := "sk.vub"
name := "sbt-nifi-nar"

sbtPlugin := true

version := "0.1-SNAPSHOT"

scalaVersion := "2.12.10"

scriptedBufferLog := false
scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value, "-Dplugin.name=" + name.value)
}

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-compress" % "1.19"
)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
