package de.gccc.jib

import com.google.cloud.tools.jib.api.{ Containerizer, DockerDaemonImage, ImageReference, Jib }
import com.google.cloud.tools.jib.api.buildplan.{ ImageFormat, Platform, Port }
import com.google.cloud.tools.jib.docker.CliDockerClient
import sbt.internal.util.ManagedLogger

import java.io.File
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private[jib] object SbtDockerBuild {

  def task(
      targetDirectory: File,
      logger: ManagedLogger,
      configuration: SbtConfiguration,
      jibBaseImageCredentialHelper: Option[String],
      jvmFlags: List[String],
      tcpPorts: List[Int],
      udpPorts: List[Int],
      args: List[String],
      entryPoint: Option[List[String]],
      environment: Map[String, String],
      labels: Map[String, String],
      additionalTags: List[String],
      user: Option[String],
      useCurrentTimestamp: Boolean,
      platforms: Set[Platform]
  ): ImageReference = {
    if (!CliDockerClient.isDefaultDockerInstalled) {
      throw new Exception("Build to Docker daemon failed")
    }

    try {
      val targetImage = DockerDaemonImage.named(configuration.targetImageReference)
      val taggedImage = Containerizer.to(targetImage)
      JibCommon.configureContainerizer(taggedImage)(
        additionalTags,
        configuration.allowInsecureRegistries,
        configuration.USER_AGENT_SUFFIX,
        targetDirectory
      )
      val baseImage = JibCommon.baseImageFactory(configuration.baseImageReference)(
        jibBaseImageCredentialHelper,
        configuration.credsForHost,
        configuration.logEvent
      )
      val container = Jib
        .from(baseImage)
        .setFileEntriesLayers(configuration.getLayerConfigurations)
        .setUser(user.orNull)
        .setEnvironment(environment.asJava)
        .setPlatforms(platforms.asJava)
        .setLabels(labels.asJava)
        .setProgramArguments(args.asJava)
        .setFormat(ImageFormat.Docker)
        .setEntrypoint(configuration.entrypoint(jvmFlags, entryPoint))
        .setExposedPorts((tcpPorts.toSet.map(s => Port.tcp(s)) ++ (udpPorts.toSet.map(s => Port.udp(s)))).asJava)
        .setCreationTime(TimestampHelper.useCurrentTimestamp(useCurrentTimestamp))
        .containerize(taggedImage)

      JibCommon.writeJibOutputFiles(container)(targetDirectory)

      logger.success("image successfully created & uploaded")
      configuration.targetImageReference
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create docker image (Exception: $t)")
        throw t
    }
  }

}
