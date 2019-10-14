import sk.vub.sbt.nifi.NarPlugin

lazy val root = (project in file("."))
  .enablePlugins(NarPlugin)
  .settings(
    scalaVersion := "2.12.10",
    version := "0.1-SNAPSHOT"
  )
