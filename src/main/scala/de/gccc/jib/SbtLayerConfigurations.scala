package de.gccc.jib

import java.io.File

import sbt._
import com.google.cloud.tools.jib.configuration.LayerConfiguration
import sbt.Keys

object SbtLayerConfigurations {

  def generate(
      classes: Seq[File],
      resourceDirectories: Seq[File],
      internalDependencies: Keys.Classpath,
      external: Keys.Classpath,
      extraMappings: Seq[(File, String)],
      specialResourceDirectory: File
  ): List[LayerConfiguration] = {

    val internalDependenciesLayer = {
      SbtJibHelper.mappingsConverter(MappingsHelper.fromClasspath(internalDependencies.seq, "/app/libs"))
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

}
