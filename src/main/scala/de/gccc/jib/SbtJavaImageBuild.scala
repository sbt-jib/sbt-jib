package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan._
import com.google.cloud.tools.jib.api._
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
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
      val targetImage = configuration.targetImageFactory(jibTargetImageCredentialHelper)
      val taggedImage =
        additionalTags.foldRight(Containerizer.to(targetImage))((tag, image) => image.withAdditionalTag(tag))

      val sbtJavaCommon = new SbtJavaCommon(logger)
      val builder = sbtJavaCommon
        .prepareJavaContainerBuilder(
          JavaContainerBuilder.from(configuration.baseImageFactory(jibBaseImageCredentialHelper)),
          configuration.layerConfigurations,
          configuration.pickedMainClass,
          jvmFlags
        )
        .toContainerBuilder
      val container = sbtJavaCommon
        .prepareJibContainerBuilder(
          builder,
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
        .containerize(configuration.configureContainerizer(taggedImage))

      SbtJibHelper.writeJibOutputFiles(targetDirectory, container)

      logger.success("java image successfully created & uploaded")
      configuration.targetImageReference
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create java image (Exception: $t)")
        throw t
    }
  }

}
