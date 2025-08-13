package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer
import de.gccc.jib.PluginCompat.Classpath
import sbt.*
import xsbti.FileConverter

import java.io.File

private[jib] case class SbtLayerConfigurations(
    targetDirectory: File,
    classes: Seq[File],
    resourceDirectories: Seq[File],
    resources: Seq[File],
    internalDependencies: Classpath,
    external: Classpath,
    extraMappings: Seq[(PluginCompat.FileRef, String)],
    extraMappingPermissions: Seq[(Glob, String)],
    specialResourceDirectory: File,
    mappings: Seq[(PluginCompat.FileRef, String)],
    addToClasspath: List[File]
)(implicit converter: FileConverter) {
  lazy val generate: List[FileEntriesLayer] = {

    val internalDependenciesLayer = {
      SbtJibHelper.mappingsConverter("internal", reproducibleDependencies(targetDirectory, internalDependencies))
    }
    val externalDependenciesLayer = {
      SbtJibHelper.mappingsConverter("libs", MappingsHelper.fromClasspath(external, "/app/libs"))
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
        SbtJibHelper.mappingsConverter(
          "extra",
          extraMappings.filter(f => PluginCompat.isFile(f._1)),
          extraMappingPermissions
        ) :: Nil
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

  private def reproducibleDependencies(targetDirectory: File, internalDependencies: Classpath) = {

    val stageDirectory = targetDirectory / "jib" / "dependency-stage"
    IO.delete(stageDirectory)
    IO.createDirectory(stageDirectory)

    val stripper = new ZipStripper()

    internalDependencies.foreach { in =>
      val file     = PluginCompat.toFile(in)
      val fileName = file.getName
      val out      = new File(stageDirectory, fileName)
      stripper.strip(file, out)
    }

    MappingsHelper.contentOf(stageDirectory, "/app/libs")
  }

}
