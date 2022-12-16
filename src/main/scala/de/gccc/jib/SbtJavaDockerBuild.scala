package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan._
import com.google.cloud.tools.jib.api._
import com.google.cloud.tools.jib.docker.CliDockerClient
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
      platforms: Set[Platform]
  ): ImageReference = {
    if (!CliDockerClient.isDefaultDockerInstalled) {
      throw new Exception("Build to Docker daemon failed")
    }

    try {
      val targetImage = DockerDaemonImage.named(configuration.targetImageReference)
      val baseImage = JibCommon.baseImageFactory(configuration.baseImageReference)(
        jibBaseImageCredentialHelper,
        configuration.credsForHost,
        _ => ()
      )
      val containerizer = JibCommon.configureContainerizer(Containerizer.to(targetImage))(
        additionalTags,
        configuration.allowInsecureRegistries,
        configuration.USER_AGENT_SUFFIX,
        configuration.target
      )
      val builder = JibCommon
        .prepareJavaContainerBuilder(JavaContainerBuilder.from(baseImage))(
          configuration.layerConfigurations,
          Some(configuration.pickedMainClass),
          jvmFlags,
          logger.warn
        )
        .toContainerBuilder
      val container = JibCommon
        .prepareJibContainerBuilder(builder)(
          tcpPorts.toSet.map(s => Port.tcp(s)) ++ udpPorts.toSet.map(s => Port.udp(s)),
          args,
          ImageFormat.Docker,
          environment,
          labels,
          user,
          useCurrentTimestamp,
          platforms
        )
        .containerize(containerizer)

      JibCommon.writeJibOutputFiles(container)(targetDirectory)

      logger.success("java image successfully created & uploaded")
      configuration.targetImageReference
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create java docker image (Exception: $t)")
        throw t
    }
  }

}
