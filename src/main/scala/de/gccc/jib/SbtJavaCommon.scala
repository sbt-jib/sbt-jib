package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan.{ ImageFormat, Platform, Port }
import com.google.cloud.tools.jib.api.{ JavaContainerBuilder, JibContainerBuilder }

import java.nio.file.Path
import scala.jdk.CollectionConverters._

private[jib] object SbtJavaCommon {

  private def isSnapshotDependency(path: Path) = path.toString.endsWith("-SNAPSHOT.jar")

  def prepareJavaContainerBuilder(
      builder: JavaContainerBuilder,
      layerConfigurations: SbtLayerConfigurations,
      mainClass: Option[String],
      jvmFlags: List[String]
  ): JavaContainerBuilder = {
    builder.addDependencies(
      layerConfigurations.external.map(_.data.toPath).filterNot(isSnapshotDependency).asJava
    )
    builder.addSnapshotDependencies(
      layerConfigurations.external.map(_.data.toPath).filter(isSnapshotDependency).asJava
    )
    builder.addToClasspath(layerConfigurations.addToClasspath.map(_.toPath).asJava)
    builder.addProjectDependencies(
      layerConfigurations.internalDependencies.map(_.data.toPath).asJava
    )
    layerConfigurations.resourceDirectories.filter(_.exists).foreach { f =>
      builder.addResources(f.toPath)
    }
    layerConfigurations.classes.filter(_.exists).foreach { f =>
      builder.addClasses(f.toPath, (p: Path) => p.toString.endsWith(".class"))
    }
    builder.setMainClass(mainClass.orNull).addJvmFlags(jvmFlags.asJava)
  }

  def prepareJibContainerBuilder(
      builder: JibContainerBuilder,
      tcpPorts: List[Int],
      udpPorts: List[Int],
      args: List[String],
      internalImageFormat: ImageFormat,
      environment: Map[String, String],
      labels: Map[String, String],
      user: Option[String],
      useCurrentTimestamp: Boolean,
      platforms: Set[Platform]
  ): JibContainerBuilder = builder
    .setEnvironment(environment.asJava)
    .setPlatforms(platforms.asJava)
    .setLabels(labels.asJava)
    .setUser(user.orNull)
    .setProgramArguments(args.asJava)
    .setFormat(internalImageFormat)
    .setExposedPorts((tcpPorts.toSet.map(s => Port.tcp(s)) ++ udpPorts.toSet.map(s => Port.udp(s))).asJava)
    .setCreationTime(TimestampHelper.useCurrentTimestamp(useCurrentTimestamp))
}
