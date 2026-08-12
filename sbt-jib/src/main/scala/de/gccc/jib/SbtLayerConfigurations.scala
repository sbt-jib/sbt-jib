package de.gccc.jib

import java.io.File
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer
import sbt._

private[jib] case class SbtLayerConfigurations(
    targetDirectory: File,
    classes: Seq[File],
    resourceDirectories: Seq[File],
    resources: Seq[File],
    internalDependencies: Seq[Attributed[File]],
    external: Seq[Attributed[File]],
    extraMappings: Seq[(File, String)],
    extraMappingPermissions: Seq[(Glob, String)],
    specialResourceDirectory: File,
    mappings: Seq[(File, String)],
    addToClasspath: List[File]
) {
  lazy val generate: List[FileEntriesLayer] = {

    val internalDependenciesLayer = {
      SbtJibHelper.mappingsConverter("internal", reproducibleDependencies(targetDirectory, internalDependencies))
    }
    val externalDependenciesLayer = {
      val libMappings = MappingsHelper.fromClasspath(external, "/app/libs").map { case (file, _) =>
        file -> s"/app/libs/${libFileName(file)}"
      }
      SbtJibHelper.mappingsConverter("libs", libMappings)
    }

    val resourcesLayer = {
      SbtJibHelper.mappingsConverter(
        "conf",
        resourceDirectories.flatMap(
          MappingsHelper.contentOf(_, "/app/resources", f => f.isFile && resources.contains(f))
        )
      )
    }

    val specialResourcesLayer = {
      SbtJibHelper.mappingsConverter(
        "resources",
        MappingsHelper.contentOf(specialResourceDirectory, "/app/resources", _.isFile)
      )
    }

    val extraLayer =
      if (extraMappings.nonEmpty)
        SbtJibHelper.mappingsConverter("extra", extraMappings.filter(_._1.isFile), extraMappingPermissions) :: Nil
      else Nil

    val allClasses = classes
      // we only want class-files in our classes layer
      // FIXME: not just extensions checking?
      .flatMap(MappingsHelper.contentOf(_, "/app/classes", f => if (f.isFile) f.getName.endsWith(".class") else false))

    val classesLayer = SbtJibHelper.mappingsConverter("classes", allClasses)

    // the ordering here is really important
    (extraLayer ::: List(
      externalDependenciesLayer,
      resourcesLayer,
      internalDependenciesLayer,
      specialResourcesLayer,
      classesLayer
    )).filterNot(lc => lc.getEntries.isEmpty)
  }

  /**
   * All dependency jars end up flat in /app/libs, so jars that share a file name (e.g. the same artifact name and
   * version published by two different organisations) would map to the same path and silently overwrite each other,
   * leaving classes missing at runtime.
   */
  private lazy val duplicateJarNames: Set[String] =
    (internalDependencies ++ external)
      .map(_.data.getName)
      .groupBy(identity)
      .collect { case (fileName, occurrences) if occurrences.size > 1 => fileName }
      .toSet

  /**
   * Renaming logic for colliding file names, kept in sync with jib-core's JavaContainerBuilder, which appends the file
   * size for the same reason. See https://github.com/GoogleContainerTools/jib/issues/3331
   */
  private def libFileName(file: File): String =
    if (duplicateJarNames.contains(file.getName)) file.getName.replaceFirst("\\.jar$", "-" + file.length) + ".jar"
    else file.getName

  private def reproducibleDependencies(targetDirectory: File, internalDependencies: Seq[Attributed[File]]) = {
    val dependencies = internalDependencies.map(_.data)

    val stageDirectory = targetDirectory / "jib" / "dependency-stage"
    IO.delete(stageDirectory)
    IO.createDirectory(stageDirectory)

    val stripper = new ZipStripper()

    dependencies.foreach { in =>
      val fileName = libFileName(in)
      val out      = new File(stageDirectory, fileName)
      stripper.strip(in, out)
    }

    MappingsHelper.contentOf(stageDirectory, "/app/libs")
  }

}
