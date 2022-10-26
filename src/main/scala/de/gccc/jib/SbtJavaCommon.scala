package de.gccc.jib

import com.google.cloud.tools.jib.api.{ JavaContainerBuilder, JibContainerBuilder, RegistryImage }
import sbt.internal.util.ManagedLogger

import java.io.File
import java.nio.file.Path
import scala.jdk.CollectionConverters._

private[jib] object SbtJavaCommon {

  private def isSnapshotDependency(path: Path) = path.toString.endsWith("-SNAPSHOT.jar")

  private def addToClasspath(
      add: java.util.List[Path] => Any,
      mappings: Seq[(File, String)],
      logger: ManagedLogger
  ): Unit = {
    add(
      mappings.map { case (file, ignored) =>
        logger.warn(s"The file `$file` won't be mapped to `$ignored` in the container, but directly to `$file`.")
        file.toPath
      }.asJava
    )
  }

  def makeJibContainerBuilder(
      baseImage: RegistryImage,
      layerConfigurations: SbtLayerConfigurations,
      mainClass: String,
      jvmFlags: List[String],
      logger: ManagedLogger
  ): JibContainerBuilder = {
    val builder = JavaContainerBuilder.from(baseImage)
    builder.addDependencies(
      layerConfigurations.external.map(_.data.toPath).filterNot(isSnapshotDependency).asJava
    )
    builder.addSnapshotDependencies(
      layerConfigurations.external.map(_.data.toPath).filter(isSnapshotDependency).asJava
    )
    addToClasspath(builder.addToClasspath, layerConfigurations.mappings, logger)
    addToClasspath(builder.addToClasspath, layerConfigurations.extraMappings, logger)
    builder.addProjectDependencies(
      layerConfigurations.internalDependencies.map(_.data.toPath).asJava
    )
    layerConfigurations.resourceDirectories.filter(_.exists).foreach { f =>
      builder.addResources(f.toPath)
    }
    layerConfigurations.classes.filter(_.exists).foreach { f =>
      builder.addClasses(f.toPath, (p: Path) => p.toString.endsWith(".class"))
    }
    builder.setMainClass(mainClass).addJvmFlags(jvmFlags.asJava).toContainerBuilder
  }
}
