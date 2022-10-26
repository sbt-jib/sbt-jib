package de.gccc.jib
import com.google.cloud.tools.jib.api.{ Containerizer, DockerDaemonImage, ImageReference, Jib }
import com.google.cloud.tools.jib.api.buildplan.{ ImageFormat, Platform }
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
      val taggedImage =
        additionalTags.foldRight(Containerizer.to(targetImage))((tag, image) => image.withAdditionalTag(tag))

      val container = Jib
        .from(configuration.baseImageFactory(jibBaseImageCredentialHelper))
        .setFileEntriesLayers(configuration.getLayerConfigurations)
        .setUser(user.orNull)
        .setEnvironment(environment.asJava)
        .setPlatforms(platforms.asJava)
        .setLabels(labels.asJava)
        .setProgramArguments(args.asJava)
        .setFormat(ImageFormat.Docker)
        .setEntrypoint(configuration.entrypoint(jvmFlags, entryPoint))
        .setCreationTime(TimestampHelper.useCurrentTimestamp(useCurrentTimestamp))
        .containerize(configuration.configureContainerizer(taggedImage))

      SbtJibHelper.writeJibOutputFiles(targetDirectory, container)

      logger.success("image successfully created & uploaded")
      configuration.targetImageReference
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create docker image (Exception: $t)")
        throw t
    }
  }

}
