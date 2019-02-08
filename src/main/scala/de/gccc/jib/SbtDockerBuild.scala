package de.gccc.jib
import com.google.cloud.tools.jib.api.{ Containerizer, DockerDaemonImage, Jib }
import com.google.cloud.tools.jib.docker.DockerClient
import com.google.cloud.tools.jib.image.ImageFormat
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
      environment: Map[String, String]
  ): Unit = {
    if (!DockerClient.isDefaultDockerInstalled) {
      throw new Exception("Build to Docker daemon failed")
    }

    try {
      val client = DockerClient.newDefaultClient()

      val targetImage = DockerDaemonImage.named(configuration.targetImageReference)

      val containerizer = Containerizer.to(targetImage).setToolName(USER_AGENT_SUFFIX)

      val jib = Jib.from(configuration.baseImageFactory(jibTargetImageCredentialHelper))

      configuration.getLayerConfigurations.forEach { configuration =>
        jib.addLayer(configuration)
      }

      jib
        .setEnvironment(environment.asJava)
        .setProgramArguments(args.asJava)
        .setFormat(ImageFormat.Docker)
        .setEntrypoint(configuration.entrypoint(jvmFlags))
        .containerize(containerizer)

      logger.info("image successfully created & uploaded")
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create docker image (Exception: $t)")
    }
  }

}
