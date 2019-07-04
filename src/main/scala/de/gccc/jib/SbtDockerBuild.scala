package de.gccc.jib
import java.nio.file.Files

import com.google.cloud.tools.jib.api.{ Containerizer, DockerDaemonImage, Jib, ImageFormat }
import com.google.cloud.tools.jib.docker.DockerClient
import sbt.internal.util.ManagedLogger

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private[jib] object SbtDockerBuild {

  private val USER_AGENT_SUFFIX = "jib-sbt-plugin"

  def task(
      logger: ManagedLogger,
      configuration: SbtConfiguration,
      jibBaseImageCredentialHelper: Option[String],
      jibTargetImageCredentialHelper: Option[String],
      defaultImage: String,
      jvmFlags: List[String],
      args: List[String],
      entryPoint: Option[List[String]],
      environment: Map[String, String],
      useCurrentTimestamp: Boolean
  ): Unit = {
    if (!DockerClient.isDefaultDockerInstalled) {
      throw new Exception("Build to Docker daemon failed")
    }

    try {
      val targetImage = DockerDaemonImage.named(configuration.targetImageReference)

      val containerizer = Containerizer
        .to(targetImage)
        .setToolName(USER_AGENT_SUFFIX)
        .setApplicationLayersCache(Files.createTempDirectory("jib-application-layer-cache"))
        .setBaseImageLayersCache(Files.createTempDirectory("jib-base-image-layer-cache"))

      Jib
        .from(configuration.baseImageFactory(jibTargetImageCredentialHelper))
        .setLayers(configuration.getLayerConfigurations)
        .setEnvironment(environment.asJava)
        .setProgramArguments(args.asJava)
        .setFormat(ImageFormat.Docker)
        .setEntrypoint(configuration.entrypoint(jvmFlags, entryPoint))
        .setCreationTime(TimestampHelper.useCurrentTimestamp(useCurrentTimestamp))
        .containerize(containerizer)

      logger.success("image successfully created & uploaded")
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create docker image (Exception: $t)")
        throw t
    }
  }

}
