import sbt.Package.ManifestAttributes

lazy val root = (project in file("."))
  .enablePlugins(NarPlugin)
  .settings(
    name := "nifi-test-bundle",
    scalaVersion := "2.12.15",
    version := "0.1-SNAPSHOT",
    nifiVersion := "1.15.3",

    libraryDependencies ++= Seq(
      "org.apache.nifi" % "nifi-api",
      "org.apache.nifi" % "nifi-utils",
      "org.apache.nifi" % "nifi-record-serialization-service-api",
    ).map(_ % nifiVersion.value),

    packageOptions := Seq(
      ManifestAttributes(
        ("Custom-Key", "Custom-Value"),
        ("Custom-Key2", "Custom-Value2"),
      ),
        ManifestAttributes(
        ("Custom-Key3", "Custom-Value3")
      )
    )
  )
