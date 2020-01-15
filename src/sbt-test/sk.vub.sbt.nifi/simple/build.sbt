import sk.vub.sbt.nifi.NarPlugin

lazy val root = (project in file("."))
  .enablePlugins(NarPlugin)
  .settings(
    name := "nifi-test-bundle",
    scalaVersion := "2.12.10",
    version := "0.1-SNAPSHOT",
    nifiVersion := "1.10.0",

    libraryDependencies ++= Seq(
      "org.apache.nifi" % "nifi-api",
      "org.apache.nifi" % "nifi-utils"
    ).map(_ % nifiVersion.value)
  )
