package de.gccc.jib

import com.google.cloud.tools.jib.api.{ JavaContainerBuilder, JibContainerBuilder, RegistryImage }

import java.nio.file.Path
import scala.jdk.CollectionConverters._

private[jib] object SbtJavaCommon {

  private def isSnapshotDependency(path: Path) = path.toString.endsWith("-SNAPSHOT.jar")

  def makeJibContainerBuilder(
      baseImage: RegistryImage,
      layerConfigurations: SbtLayerConfigurations,
      mainClass: String,
      jvmFlags: List[String]
  ): JibContainerBuilder = {
    val builder = JavaContainerBuilder.from(baseImage)
    builder.addDependencies(
      layerConfigurations.external.map(_.data.toPath).filterNot(isSnapshotDependency).asJava
    )
    builder.addSnapshotDependencies(
      layerConfigurations.external.map(_.data.toPath).filter(isSnapshotDependency).asJava
    )
    builder.addToClasspath(
      layerConfigurations.extraMappings.map { case (file, _) => file.toPath }.asJava
    )
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
