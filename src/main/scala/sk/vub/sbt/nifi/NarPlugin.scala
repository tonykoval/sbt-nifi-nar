package sk.vub.sbt.nifi

import java.time.format.{DateTimeFormatterBuilder, SignStyle}
import java.time.temporal.ChronoField._
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.{Date, Locale}

import buildinfo.BuildInfo
import sbt.Keys._
import sbt._
import sbt.io.IO

import scala.util.Try
import scala.util.matching.Regex

trait NarKeys {
  val narTargetDir = settingKey[File]("target directory to pack, default is target")
  val narDir = settingKey[String]("nar directory name")
  val narLibJars = taskKey[Seq[(File, ProjectRef)]]("nar-lib-jars")
  val narExclude = settingKey[Seq[String]]("specify projects whose dependencies will be excluded when packaging")
  val narExcludeLibJars = settingKey[Seq[String]]("specify projects to exclude when packaging.  Its dependencies will be processed")
  val narExcludeJars = settingKey[Seq[String]]("specify jar file name patterns to exclude when packaging")
  val narExcludeArtifactTypes = settingKey[Seq[String]]("specify artifact types (e.g. javadoc) to exclude when packaging")
  val narAllUnmanagedJars = taskKey[Seq[(Classpath, ProjectRef)]]("all unmanaged jar files")
  val narModuleEntries = taskKey[Seq[ModuleEntry]]("modules that will be packed")
  val narDuplicateJarStrategy = settingKey[String]("deal with duplicate jars. default to use latest version latest: use the jar with a higher version; exit: exit the task with error")
  val narJarNameConvention = settingKey[String]("default: (artifact name)-(version).jar; original: original JAR name; full: (organization).(artifact name)-(version).jar; no-version: (organization).(artifact name).jar")

  val narDependencyGroupId = settingKey[String]("nar dependency group id, default: org.apache.nifi")
  val narDependencyArtifactId = settingKey[String]("nar dependency artifact id, default: nifi-standard-services-api-nar")

  val nifiVersion = settingKey[String]("nifi version, mandatory (e.g. 1.9.2)")
  val nar = taskKey[File]("create a nar package of the project")
}

case class ModuleEntry(org: String, name: String, revision: VersionString, artifactName: String, classifier: Option[String], file: File) {
  private def classifierSuffix = classifier.map("-" + _).getOrElse("")

  override def toString: String = "%s:%s:%s%s".format(org, artifactName, revision, classifierSuffix)
  def originalFileName: String = file.getName
  def jarName: String = "%s-%s%s.jar".format(artifactName, revision, classifierSuffix)
  def fullJarName: String = "%s.%s-%s%s.jar".format(org, artifactName, revision, classifierSuffix)
  def noVersionJarName: String = "%s.%s%s.jar".format(org, artifactName, classifierSuffix)
  def noVersionModuleName: String = "%s.%s%s.jar".format(org, name, classifierSuffix)
  def toDependencyStr: String = s""""${org}" % "${name}" % "${revision}""""
}

object autoImport extends NarKeys

object NarPlugin extends AutoPlugin {

  override val trigger: PluginTrigger = noTrigger

  override val requires: Plugins = plugins.JvmPlugin

  object autoImport extends NarKeys
  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    narTargetDir := target.value,
    narDir := "nar",
    narExclude := Seq.empty,
    narExcludeLibJars := Seq.empty,
    narExcludeJars := Seq.empty,
    narExcludeArtifactTypes := Seq("source", "javadoc", "test"),
    narDuplicateJarStrategy := "latest",
    narJarNameConvention := "default",
    narDependencyGroupId := "org.apache.nifi",
    narDependencyArtifactId := "nifi-standard-services-api-nar",
    (mappings in nar) := Seq.empty,
    narAllUnmanagedJars := Def.taskDyn {
      val allUnmanagedJars = getFromSelectedProjects(thisProjectRef.value, unmanagedJars in Runtime, state.value, narExclude.value)
      Def.task { allUnmanagedJars.value }
    }.value,
    narLibJars := Def.taskDyn {
      val libJars = getFromSelectedProjects(thisProjectRef.value, packageBin in Runtime, state.value, narExcludeLibJars.value)
      Def.task { libJars.value }
    }.value,
    narModuleEntries := {
      val out = streams.value
      val jarExcludeFilter: Seq[Regex] = narExcludeJars.value.map(_.r)
      def isExcludeJar(name: String): Boolean = {
        val toExclude = jarExcludeFilter.exists(pattern => pattern.findFirstIn(name).isDefined)
        if (toExclude) {
          out.log.info(s"Exclude $name from the package")
        }
        toExclude
      }

      val df = configurationFilter(name = "runtime")

      val dependentJars =
        for {
          c <- update.value.filter(df).configurations
          m <- c.modules if !m.evicted
          (artifact, file) <- m.artifacts
          if !narExcludeArtifactTypes.value.contains(artifact.`type`) && !isExcludeJar(file.name)
        } yield {
          val mid = m.module
          ModuleEntry(mid.organization, mid.name, VersionString(mid.revision), artifact.name, artifact.classifier, file)
        }

      implicit val versionStringOrdering = DefaultVersionStringOrdering
      val distinctDpJars = dependentJars
        .groupBy(_.noVersionModuleName)
        .flatMap {
          case (_, entries) if entries.groupBy(_.revision).size == 1 => entries
          case (key, entries) =>
            val revisions      = entries.groupBy(_.revision).keys.toList.sorted
            val latestRevision = revisions.last
            narDuplicateJarStrategy.value match {
              case "latest" =>
                out.log.debug(s"Version conflict on $key. Using ${latestRevision} (found ${revisions.mkString(", ")})")
                entries.filter(_.revision == latestRevision)
              case "exit" =>
                sys.error(s"Version conflict on $key (found ${revisions.mkString(", ")})")
              case x =>
                sys.error("Unknown duplicate JAR strategy '%s'".format(x))
            }
        }
      distinctDpJars.toSeq
    },
    nar := {
      val out = streams.value
      val logPrefix = "[" + name.value + "] "
      val base: File = new File(".") // Using the working directory as base for readability

      val distDir: File = narTargetDir.value / narDir.value / "META-INF"
      out.log.info(logPrefix + "Creating a distributable package in " + rpath(base, distDir))
      IO.delete(distDir)
      distDir.mkdirs()

      // Create target/pack/lib folder
      val libDir = distDir / "bundled-dependencies"
      libDir.mkdirs()

      // Copy project jars
      out.log.info(logPrefix  + "Copying libraries to " + rpath(base, libDir))
      val libs: Seq[File] = narLibJars.value.map(_._1)
      out.log.info(logPrefix + "project jars:\n" + libs.map(path => rpath(base, path)).mkString("\n"))
      libs.foreach(l => IO.copyFile(l, libDir / l.getName))

      // Copy dependent jars
      val distinctDpJars = narModuleEntries.value
      out.log.info(logPrefix + "project dependencies:\n" + distinctDpJars.mkString("\n"))
      val jarNameConvention = narJarNameConvention.value
      for (m <- distinctDpJars) {
        val targetFileName = resolveJarName(m, jarNameConvention)
        IO.copyFile(m.file, libDir / targetFileName, preserveLastModified = true)
      }

      // Copy unmanaged jars in ${baseDir}/lib folder
      out.log.info(logPrefix + "unmanaged dependencies:")
      for ((m, _) <- narAllUnmanagedJars.value; um <- m; f = um.data) {
        out.log.info(f.getPath)
        IO.copyFile(f, libDir / f.getName, preserveLastModified = true)
      }

      // Copy explicitly added dependencies
      val mapped: Seq[(File, String)] = (mappings in nar).value
      out.log.info(logPrefix + "explicit dependencies:")
      for ((file, path) <- mapped) {
        out.log.info(file.getPath)
        IO.copyFile(file, distDir / path, preserveLastModified = true)
      }

      def write(path: String, content: String) {
        val p = distDir / path
        out.log.info(logPrefix + "Generating %s".format(rpath(base, p)))
        IO.write(p, content)
      }

      val systemZone = ZoneId.systemDefault().normalized()
      val timestamp  = ZonedDateTime.ofInstant(Instant.ofEpochMilli(new Date().getTime), systemZone)
      val buildTime  = humanReadableTimestampFormatter.format(timestamp)

      // Check the current Git revision
      val gitRevision: String = Try {
        if((base / ".git").exists()) {
          out.log.info(logPrefix + "Checking the git revision of the current project")
          sys.process.Process("git rev-parse HEAD").!!
        }
        else {
          "unknown"
        }
      }.getOrElse("unknown").trim

      val gitBranch: String = Try {
        if((base / ".git").exists()) {
          out.log.info(logPrefix + "Checking the git branch of the current project")
          sys.process.Process("git branch | grep \\* | cut -d ' ' -f2").!!
        }
        else {
          "unknown"
        }
      }.getOrElse("unknown").trim

      write("MANIFEST.MF",
  "Manifest-Version: 1.0\n" +
          s"Build-Branch: ${gitBranch}\n" +
          s"Build-Timestamp: ${buildTime}\n" +
          "Archiver-Version: Plexus Archiver\n" +
          s"Nar-Dependency-Group: ${narDependencyGroupId.value}\n" +
          s"Nar-Id: ${name.value}\n" +
          "Clone-During-Instance-Class-Loading: false\n" +
          s"Nar-Dependency-Version: ${nifiVersion.value}\n" +
          s"Nar-Version: ${version.value}\n" +
          s"Build-Revision: ${gitRevision}\n" +
          s"Nar-Group: ${organization.value}\n" +
          s"Nar-Dependency-Id: ${narDependencyArtifactId.value}\n" +
          s"Created-By: ${BuildInfo.name}-${BuildInfo.version}\n" +
          "Build-Jdk: ??? (1.8.0_222)\n"
      )

      out.log.info(logPrefix + "done.")
      distDir
    }
  )

  private val humanReadableTimestampFormatter = new DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
    .appendLiteral('-')
    .appendValue(MONTH_OF_YEAR, 2)
    .appendLiteral('-')
    .appendValue(DAY_OF_MONTH, 2)
    .appendLiteral('T')
    .appendValue(HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(MINUTE_OF_HOUR, 2)
    .appendLiteral(':')
    .appendValue(SECOND_OF_MINUTE, 2)
    .appendOffset("+HHMM", "Z")
    .toFormatter(Locale.US)

  // copy from sbt-pack
  private def getFromSelectedProjects[T](
    contextProject:ProjectRef,
    targetTask: TaskKey[T],
    state: State,
    exclude: Seq[String]
  ): Task[Seq[(T, ProjectRef)]] = {
    val extracted = Project.extract(state)
    val structure = extracted.structure

    def transitiveDependencies(currentProject: ProjectRef): Seq[ProjectRef] = {
      def isExcluded(p: ProjectRef) = exclude.contains(p.project)

      // Traverse all dependent projects
      val children = Project
        .getProject(currentProject, structure)
        .toSeq
        .flatMap{ _.dependencies.map(_.project) }

      (currentProject +: (children flatMap transitiveDependencies)) filterNot (isExcluded)
    }
    val projects: Seq[ProjectRef] = transitiveDependencies(contextProject).distinct
    projects.map(p => (Def.task { ((targetTask in p).value, p) }) evaluate structure.data).join
  }

  private def resolveJarName(m: ModuleEntry, convention: String) = {
    convention match {
      case "original"   => m.originalFileName
      case "full"       => m.fullJarName
      case "no-version" => m.noVersionJarName
      case _            => m.jarName
    }
  }
}
