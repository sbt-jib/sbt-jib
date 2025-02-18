package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan._
import com.google.cloud.tools.jib.api._
import com.google.cloud.tools.jib.docker.CliDockerClient
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.internal.util.ManagedLogger

import java.io.File
import scala.util.control.NonFatal

private[jib] object SbtJavaDockerBuild {

  def task(
      targetDirectory: File,
      logger: ManagedLogger,
      configuration: SbtConfiguration,
      jibBaseImageCredentialHelper: Option[String],
      jvmFlags: List[String],
      tcpPorts: List[Int],
      udpPorts: List[Int],
      args: List[String],
      environment: Map[String, String],
      labels: Map[String, String],
      additionalTags: List[String],
      user: Option[String],
      useCurrentTimestamp: Boolean,
      platforms: Set[Platform],
      volumes: List[String],
      workingDirectory: Option[String]
  ): ImageReference = {
    if (!CliDockerClient.isDefaultDockerInstalled) {
      throw new Exception("Build to Docker daemon failed")
    }
    try {
      val targetImage   = DockerDaemonImage.named(configuration.targetImageReference)
      val containerizer = Containerizer.to(targetImage)
      SbtJibHelper.javaBuild(
        targetDirectory,
        configuration,
        jibBaseImageCredentialHelper,
        jvmFlags,
        tcpPorts,
        udpPorts,
        args,
        JibImageFormat.Docker,
        environment,
        labels,
        additionalTags,
        user,
        useCurrentTimestamp,
        platforms,
        volumes,
        workingDirectory
      )(containerizer)
      logger.success("java image successfully created & uploaded")
      configuration.targetImageReference
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create java docker image (Exception: $t)")
        throw t
    }
  }

}
