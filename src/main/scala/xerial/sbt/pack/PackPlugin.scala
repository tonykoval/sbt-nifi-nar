package xerial.sbt.pack

import java.io.File
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, SignStyle}
import java.time.temporal.ChronoField.{DAY_OF_MONTH, HOUR_OF_DAY, MINUTE_OF_HOUR, MONTH_OF_YEAR, SECOND_OF_MINUTE, YEAR}
import java.util.Locale
import sbt.{ClasspathDep, Def, Project, ProjectRef, State, Task, TaskKey}

object PackPlugin {
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

  val humanReadableTimestampFormatter: DateTimeFormatter = new DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
    .appendLiteral('-')
    .appendValue(MONTH_OF_YEAR, 2)
    .appendLiteral('-')
    .appendValue(DAY_OF_MONTH, 2)
    .appendLiteral(' ')
    .appendValue(HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(MINUTE_OF_HOUR, 2)
    .appendLiteral(':')
    .appendValue(SECOND_OF_MINUTE, 2)
    .appendOffset("+HHMM", "Z")
    .toFormatter(Locale.US)

  // copy from sbt-pack
  def getFromSelectedProjects[T](
    contextProject: ProjectRef,
    targetTask: TaskKey[T],
    state: State,
    exclude: Seq[String]
  ): Task[Seq[(T, ProjectRef)]] = {
    val extracted = Project.extract(state)
    val structure = extracted.structure

    def transitiveDependencies(currentProject: ProjectRef): Seq[ProjectRef] = {
      def isExcluded(p: ProjectRef) = exclude.contains(p.project)

      def isCompileConfig(cp: ClasspathDep[ProjectRef]) = cp.configuration.forall(_.contains("compile->"))

      // Traverse all dependent projects
      val children = Project
        .getProject(currentProject, structure)
        .toSeq
        .flatMap { _.dependencies.filter(isCompileConfig).map(_.project) }

      (currentProject +: (children flatMap transitiveDependencies)) filterNot (isExcluded)
    }
    val projects: Seq[ProjectRef] = transitiveDependencies(contextProject).distinct
    projects.map(p => (Def.task { ((p / targetTask).value, p) }) evaluate structure.data).join
  }

  def resolveJarName(m: ModuleEntry, convention: String): String = {
    convention match {
      case "original"   => m.originalFileName
      case "full"       => m.fullJarName
      case "no-version" => m.noVersionJarName
      case _            => m.jarName
    }
  }
}
