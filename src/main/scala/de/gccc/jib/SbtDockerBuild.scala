package de.gccc.jib
import com.google.cloud.tools.jib.api.{Containerizer, DockerDaemonImage, ImageReference, Jib}
import com.google.cloud.tools.jib.api.buildplan.ImageFormat
import com.google.cloud.tools.jib.docker.DockerClient
import sbt.internal.util.ManagedLogger

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private[jib] object SbtDockerBuild {

  def task(
      logger: ManagedLogger,
      configuration: SbtConfiguration,
      jibBaseImageCredentialHelper: Option[String],
      jvmFlags: List[String],
      args: List[String],
      entryPoint: Option[List[String]],
      environment: Map[String, String],
      labels: Map[String, String],
      user: Option[String],
      useCurrentTimestamp: Boolean,
  ): ImageReference = {
    if (!DockerClient.isDefaultDockerInstalled) {
      throw new Exception("Build to Docker daemon failed")
    }

    try {
      val targetImage = DockerDaemonImage.named(configuration.targetImageReference)

      Jib
        .from(configuration.baseImageFactory(jibBaseImageCredentialHelper))
        .setFileEntriesLayers(configuration.getLayerConfigurations)
        .setUser(user.orNull)
        .setEnvironment(environment.asJava)
        .setLabels(labels.asJava)
        .setProgramArguments(args.asJava)
        .setFormat(ImageFormat.Docker)
        .setEntrypoint(configuration.entrypoint(jvmFlags, entryPoint))
        .setCreationTime(TimestampHelper.useCurrentTimestamp(useCurrentTimestamp))
        .containerize(configuration.configureContainerizer(Containerizer.to(targetImage)))

      logger.success("image successfully created & uploaded")
      return configuration.targetImageReference
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create docker image (Exception: $t)")
        throw t
    }
  }

}
