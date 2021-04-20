package de.gccc.jib

import java.io.File
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer
import sbt._

object SbtLayerConfigurations {

  def generate(
      targetDirectory: File,
      classes: Seq[File],
      resourceDirectories: Seq[File],
      resources: Seq[File],
      internalDependencies: Keys.Classpath,
      external: Keys.Classpath,
      extraMappings: Seq[(File, String)],
      specialResourceDirectory: File
  ): List[FileEntriesLayer] = {

    val internalDependenciesLayer = {
      SbtJibHelper.mappingsConverter("internal", reproducibleDependencies(targetDirectory, internalDependencies))
    }
    val externalDependenciesLayer = {
      SbtJibHelper.mappingsConverter("libs", MappingsHelper.fromClasspath(external.seq, "/app/libs"))
    }

    val resourcesLayer = {
      SbtJibHelper.mappingsConverter(
        "conf",
        resourceDirectories.flatMap(MappingsHelper.contentOf(_, "/app/resources", f => f.isFile && resources.contains(f)))
      )
    }

    val specialResourcesLayer = {
      SbtJibHelper.mappingsConverter("resources",
                                     MappingsHelper.contentOf(specialResourceDirectory, "/app/resources", _.isFile))
    }

    val extraLayer =
      if (extraMappings.nonEmpty) SbtJibHelper.mappingsConverter("extra", extraMappings.filter(_._1.isFile)) :: Nil
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

  private def reproducibleDependencies(targetDirectory: File, internalDependencies: Keys.Classpath) = {
    val dependencies = internalDependencies.seq.map(_.data)

    val stageDirectory = targetDirectory / "jib" / "dependency-stage"
    IO.delete(stageDirectory)
    IO.createDirectory(stageDirectory)

    val stripper = new ZipStripper()

    dependencies.foreach { in =>
      val fileName = in.getName
      val out      = new File(stageDirectory, fileName)
      stripper.strip(in, out)
    }

    MappingsHelper.contentOf(stageDirectory, "/app/libs")
  }

}
