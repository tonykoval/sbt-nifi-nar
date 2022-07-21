package com.github.tonykoval.sbt.nifi

import org.apache.commons.compress.archivers.zip.*
import org.apache.commons.compress.utils.IOUtils
import org.clapper.classutil.ClassFinder
import sbt.Keys.*
import sbt.Package.ManifestAttributes
import sbt.io.IO
import sbt.{Def, *}
import xerial.sbt.pack.PackPlugin.*
import xerial.sbt.pack.*

import java.io.{File as _, *}
import java.time.Instant
import java.util.jar.Attributes
import scala.util.Try
import scala.util.matching.Regex

trait NarKeys {
  val narTargetDir: SettingKey[File] = settingKey[File]("target directory to pack, default is target")
  val narDir: SettingKey[String] = settingKey[String]("nar directory name")
  val narLibJars: TaskKey[Seq[(File, ProjectRef)]] = taskKey[Seq[(File, ProjectRef)]]("nar-lib-jars")
  val narExclude: SettingKey[Seq[String]] = settingKey[Seq[String]]("specify projects whose dependencies will be excluded when packaging")
  val narExcludeLibJars: SettingKey[Seq[String]] = settingKey[Seq[String]]("specify projects to exclude when packaging.  Its dependencies will be processed")
  val narExcludeJars: SettingKey[Seq[String]] = settingKey[Seq[String]]("specify jar file name patterns to exclude when packaging")
  val narExcludeArtifactTypes: SettingKey[Seq[String]] = settingKey[Seq[String]]("specify artifact types (e.g. javadoc) to exclude when packaging")
  val narAllUnmanagedJars: TaskKey[Seq[(Classpath, ProjectRef)]] = taskKey[Seq[(Classpath, ProjectRef)]]("all unmanaged jar files")
  val narModuleEntries: TaskKey[Seq[ModuleEntry]] = taskKey[Seq[ModuleEntry]]("modules that will be packed")
  val narDuplicateJarStrategy: SettingKey[String] = settingKey[String]("deal with duplicate jars. default to use latest version latest: use the jar with a higher version; exit: exit the task with error")
  val narJarNameConvention: SettingKey[String] = settingKey[String]("default: (artifact name)-(version).jar; original: original JAR name; full: (organization).(artifact name)-(version).jar; no-version: (organization).(artifact name).jar")
  val narArchiveName: SettingKey[String] = settingKey[String]("nar file name. Default is (project-name)-(version)")
  val nifiVersion: SettingKey[String] = settingKey[String]("nifi version, mandatory (e.g. 1.10.0)")
  val narDependencyGroupId: SettingKey[String] = settingKey[String]("nar dependency group id, default: org.apache.nifi")
  val narDependencyArtifactId: SettingKey[String] = settingKey[String]("nar dependency artifact id, default: nifi-standard-services-api-nar")
  val generateDocDir: SettingKey[String] = settingKey[String]("documentation directory name, default: docs")

  val nar: TaskKey[File] = taskKey[File]("create a nar folder of the project")
  val narArchive: TaskKey[File] = taskKey[File]("create a nar package of the project")
  val findAllProcessors: TaskKey[Seq[String]] = taskKey[Seq[String]]("find all nifi processors of the project")
  val printAllProcessors: InputKey[Unit] = inputKey[Unit]("find and print all nifi processors of the project")
}

object autoImport extends NarKeys

object NarPlugin extends AutoPlugin {

  override val trigger: PluginTrigger = noTrigger

  override val requires: Plugins = plugins.JvmPlugin

  object autoImport extends NarKeys
  import autoImport.*

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
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
    (nar / mappings) := Seq.empty,
    narArchiveName := s"${name.value}-${version.value}",
    narAllUnmanagedJars := Def.taskDyn {
      val allUnmanagedJars = getFromSelectedProjects(thisProjectRef.value, (Runtime / unmanagedJars), state.value, narExclude.value)
      Def.task { allUnmanagedJars.value }
    }.value,
    narLibJars := Def.taskDyn {
      val libJars = getFromSelectedProjects(thisProjectRef.value, (Runtime / packageBin), state.value, narExcludeLibJars.value)
      Def.task { libJars.value }
    }.value,
    narModuleEntries := {
      // copy from pack
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

      implicit val versionStringOrdering: DefaultVersionStringOrdering.type = DefaultVersionStringOrdering
      val distinctDpJars = dependentJars
        .groupBy(_.noVersionModuleName)
        .flatMap {
          case (_, entries) if entries.groupBy(_.revision).size == 1 => entries
          case (key, entries) =>
            val revisions      = entries.groupBy(_.revision).keys.toList.sorted
            val latestRevision = revisions.last
            narDuplicateJarStrategy.value match {
              case "latest" =>
                out.log.debug(s"Version conflict on $key. Using $latestRevision (found ${revisions.mkString(", ")})")
                entries.filter(_.revision == latestRevision)
              case "exit" =>
                sys.error(s"Version conflict on $key (found ${revisions.mkString(", ")})")
              case x =>
                sys.error("Unknown duplicate JAR strategy '%s'".format(x))
            }
        }
      distinctDpJars.toSeq.distinct.sortBy(_.noVersionModuleName)
    },
    nar := {
      // inspired from pack
      val out = streams.value
      val logPrefix = "[" + name.value + "] "
      val base: File = new File(".") // Using the working directory as base for readability

      val distDir: File = narTargetDir.value / narDir.value
      out.log.info(logPrefix + "Creating a distributable package in " + rpath(base, distDir))
      IO.delete(distDir)
      distDir.mkdirs()

      // Create target/pack/lib folder
      val libDir = distDir / "META-INF" / "bundled-dependencies"
      libDir.mkdirs()

      // Copy project jars
      out.log.info(logPrefix  + "Copying libraries to " + rpath(base, libDir))
      val libs: Seq[File] = narLibJars.value.map(_._1)
      out.log.info(logPrefix + "project jars:\n" + libs.map(path => rpath(base, path)).mkString("\n"))
      val projectJars = libs.map(l => {
        val dest = libDir / l.getName
        IO.copyFile(l, dest)
        dest
      })

      // Copy dependent jars
      val distinctDpJars = narModuleEntries.value
      out.log.info(logPrefix + "Copying project dependencies:")
      val jarNameConvention = narJarNameConvention.value
      val projectDepsJars = for (m <- distinctDpJars) yield {
        val targetFileName = resolveJarName(m, jarNameConvention)
        val dest           = libDir / targetFileName
        out.log.info(s"$m")
        IO.copyFile(m.file, dest, preserveLastModified = true)
        dest
      }

      // Copy unmanaged jars in ${baseDir}/lib folder
      out.log.info(logPrefix + "Copying unmanaged dependencies:")
      val unmanagedDepsJars = for ((m, _) <- narAllUnmanagedJars.value; um <- m; f = um.data) yield {
        out.log.info(f.getPath)
        val dest = libDir / f.getName
        IO.copyFile(f, dest, preserveLastModified = true)
        dest
      }

      // Copy explicitly added dependencies
      val mapped: Seq[(File, String)] = (nar / mappings).value
      out.log.info(logPrefix + "Copying explicit dependencies:")
      val explicitDepsJars = for ((file, path) <- mapped) yield {
        out.log.info(file.getPath)
        val dest = distDir / path
        IO.copyFile(file, dest, preserveLastModified = true)
        dest
      }

      // put the list of jars in a file
      val bw = new BufferedWriter(new FileWriter(distDir / "META-INF" / "DEPENDENCIES"))
      for (line <- projectJars ++ projectDepsJars ++ unmanagedDepsJars ++ explicitDepsJars) {
        bw.write(line.relativeTo(distDir).get.toString)
        bw.newLine()
      }
      bw.close()

      def write(path: String, content: String): Unit = {
        val p = distDir / "META-INF" / path
        out.log.info(logPrefix + "Generating %s".format(rpath(base, p)))
        IO.write(p, content)
      }

      val buildTime = Instant.now().toString

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
          sys.process.Process("git branch").!!.replaceAll("\\*","").trim()
        }
        else {
          "unknown"
        }
      }.getOrElse("unknown").trim

      val buildJdk = sys.props("java.runtime.version")

      val packageOpts = packageOptions.value
      val customManifestAttributes = packageOpts.flatMap {
        case x: ManifestAttributes => Some(x)
        case _ => None
      }

      val manifestAttribute = ManifestAttributes(
        ("Manifest-Version", "1.0")
      )

      val coreManifestAttributes = ManifestAttributes(
        ("Build-Branch", gitBranch),
        ("Build-Timestamp", buildTime),
        ("Archiver-Version", "Plexus Archiver"),
        ("Nar-Dependency-Group", narDependencyGroupId.value),
        ("Nar-Id", name.value),
        ("Nar-Version", version.value),
        ("Clone-During-Instance-Class-Loading", "false"),
        ("Nar-Dependency-Version", nifiVersion.value),
        ("Build-Revision", gitRevision),
        ("Nar-Group", organization.value),
        ("Nar-Dependency-Id", narDependencyArtifactId.value),
        ("Created-By", s"${BuildInfo.name}-${BuildInfo.version}"),
        ("Build-Jdk", buildJdk)
      )

      // todo (check another property in maven plugin

      val mergeManifestAttributes: Seq[(Attributes.Name, String)] =
        manifestAttribute.attributes.toSeq ++
          (coreManifestAttributes.attributes.toMap ++ customManifestAttributes.flatMap(_.attributes.toMap))
            .toSeq.sortWith(_._1.toString < _._1.toString)

      write("MANIFEST.MF",
        mergeManifestAttributes
          .map(x => s"${x._1}: ${x._2}")
          .mkString("\n")
        + "\n"
      )

      out.log.info(logPrefix + "done.")
      distDir
    },
    narArchive := {
      // inspired from createArchive in PackArchive
      val out = streams.value
      val targetDir: File = narTargetDir.value
      val distDir: File = nar.value // run nar command here
      val archiveName = s"${narArchiveName.value}.nar"
      out.log.info("Generating " + rpath(baseDirectory.value, targetDir / archiveName))
      val aos = new ZipArchiveOutputStream(new BufferedOutputStream(new FileOutputStream(targetDir / archiveName)))
      def addFilesToArchive(dir: File): Unit =
        Option(dir.listFiles)
          .getOrElse(Array.empty)
          .foreach { file =>
            aos.putArchiveEntry(new ZipArchiveEntry(file, rpath(distDir, file, "/")))
            if (file.isDirectory) {
              aos.closeArchiveEntry()
              addFilesToArchive(file)
            } else {
              val in = new BufferedInputStream(new FileInputStream(file))
              try {
                IOUtils.copy(in, aos)
                aos.closeArchiveEntry()
              } finally {
                if (in != null)
                  in.close()
              }
            }
          }
      addFilesToArchive(distDir)
      aos.close()
      targetDir / archiveName
    },
    findAllProcessors := {
      val classpath = (Compile / fullClasspath).value.map(_.data)
      val classFinder = ClassFinder(classpath)
      ClassFinder
        .concreteSubclasses("org.apache.nifi.processor.AbstractProcessor", classFinder.getClasses())
        .map(x => x.name)
        .toSeq
    },
    printAllProcessors := {
      val processors = findAllProcessors.value
      println("\u001B[36mFound processors: \u001B[0m")
      processors.foreach(x => println(s"\u001B[1m${x}\u001B[0m"))
    }
  )
}
