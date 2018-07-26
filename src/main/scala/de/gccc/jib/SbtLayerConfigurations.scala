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
        resourceDirectories.flatMap(MappingsHelper.contentOf(_, "/app/resources"))
      )
    }

    val specialResourcesLayer = {
      SbtJibHelper.mappingsConverter(MappingsHelper.contentOf(specialResourceDirectory, "/app/resources"))
    }

    val extraLayer = if (extraMappings.nonEmpty) SbtJibHelper.mappingsConverter(extraMappings) :: Nil else Nil

    val classesLayer = {
      SbtJibHelper.mappingsConverter(classes.flatMap(MappingsHelper.contentOf(_, "/app/classes")))
    }

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
