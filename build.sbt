enablePlugins(SbtPlugin, ScriptedPlugin)

organization := "sk.vub"
name := "sbt-nifi-nar"

sbtPlugin := true

version := "0.1-SNAPSHOT"

scalaVersion := "2.12.10"

scriptedBufferLog := false
scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
