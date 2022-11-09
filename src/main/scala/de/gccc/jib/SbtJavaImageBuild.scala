package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan._
import com.google.cloud.tools.jib.api._
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.Credentials
import sbt.internal.util.ManagedLogger

import java.io.File
import scala.util.control.NonFatal

private[jib] object SbtJavaImageBuild {

  def task(
      targetDirectory: File,
      logger: ManagedLogger,
      configuration: SbtConfiguration,
      jibBaseImageCredentialHelper: Option[String],
      jibTargetImageCredentialHelper: Option[String],
      jvmFlags: List[String],
      tcpPorts: List[Int],
      udpPorts: List[Int],
      args: List[String],
      imageFormat: JibImageFormat,
      environment: Map[String, String],
      labels: Map[String, String],
      additionalTags: List[String],
      user: Option[String],
      useCurrentTimestamp: Boolean,
      platforms: Set[Platform]
  ): ImageReference = {

    val internalImageFormat = imageFormat match {
      case JibImageFormat.Docker => ImageFormat.Docker
      case JibImageFormat.OCI    => ImageFormat.OCI
    }

    try {
      JibCommon.setSendCredentialsOverHttp(configuration.sendCredentialsOverHttp)

      val credsForHost = Credentials.forHost(configuration.credentials, _).map(c => (c.userName, c.passwd))
      val baseImage = JibCommon
        .baseImageFactory(configuration.baseImageReference)(jibBaseImageCredentialHelper, credsForHost, _ => ())
      val repository =
        configuration.customRepositoryPath.getOrElse(configuration.organization + "/" + configuration.name)
      val targetImageReference = ImageReference.of(configuration.registry, repository, configuration.version)
      val targetImage =
        JibCommon.targetImageFactory(targetImageReference)(jibTargetImageCredentialHelper, credsForHost, _ => ())
      val containerizer = Containerizer.to(targetImage)
      JibCommon.configureContainerizer(containerizer)(
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
          tcpPorts,
          udpPorts,
          args,
          internalImageFormat,
          environment,
          labels,
          user,
          useCurrentTimestamp,
          platforms
        )
        .containerize(containerizer)

      JibCommon.writeJibOutputFiles(container)(targetDirectory)

      logger.success("java image successfully created & uploaded")
      targetImageReference
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create java image (Exception: $t)")
        throw t
    }
  }

}
