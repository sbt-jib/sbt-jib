package de.gccc.jib

import java.io.File

import com.google.cloud.tools.jib.configuration.LayerConfiguration
import sbt.Keys.target
import sbt.{ Keys, _ }

object SbtLayerConfigurations {

  def generate(
      targetDirectory: File,
      classes: Seq[File],
      resourceDirectories: Seq[File],
      internalDependencies: Keys.Classpath,
      external: Keys.Classpath,
      extraMappings: Seq[(File, String)],
      specialResourceDirectory: File
  ): List[LayerConfiguration] = {

    val internalDependenciesLayer = {
      SbtJibHelper.mappingsConverter(reproducibleDependencies(targetDirectory, internalDependencies))
    }
    val externalDependenciesLayer = {
      SbtJibHelper.mappingsConverter(MappingsHelper.fromClasspath(external.seq, "/app/libs"))
    }

    val resourcesLayer = {
      SbtJibHelper.mappingsConverter(
        resourceDirectories.flatMap(MappingsHelper.contentOf(_, "/app/resources", _.isFile))
      )
    }

    val specialResourcesLayer = {
      SbtJibHelper.mappingsConverter(MappingsHelper.contentOf(specialResourceDirectory, "/app/resources", _.isFile))
    }

    val extraLayer = if (extraMappings.nonEmpty) SbtJibHelper.mappingsConverter(extraMappings.filter(_._1.isFile)) :: Nil else Nil

    val allClasses = classes
    // we only want class-files in our classes layer
    // FIXME: not just extensions checking?
      .flatMap(MappingsHelper.contentOf(_, "/app/classes", f => if (f.isFile) f.getName.endsWith(".class") else false))

    val classesLayer = SbtJibHelper.mappingsConverter(allClasses)

    // the ordering here is really important
    extraLayer ::: List(
      externalDependenciesLayer,
      resourcesLayer,
      internalDependenciesLayer,
      specialResourcesLayer,
      classesLayer
    )
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
