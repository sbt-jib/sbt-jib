package de.gccc.jib

import java.nio.file.Files

import com.google.cloud.tools.jib.api.{ Containerizer, Jib }
import com.google.cloud.tools.jib.image.ImageFormat
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.internal.util.ManagedLogger

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private[jib] object SbtImageBuild {

  private val USER_AGENT_SUFFIX = "jib-sbt-plugin"

  def task(
      logger: ManagedLogger,
      configuration: SbtConfiguration,
      jibBaseImageCredentialHelper: Option[String],
      jibTargetImageCredentialHelper: Option[String],
      jvmFlags: List[String],
      args: List[String],
      imageFormat: JibImageFormat,
      environment: Map[String, String]
  ): Unit = {

    val internalImageFormat = imageFormat match {
      case JibImageFormat.Docker => ImageFormat.Docker
      case JibImageFormat.OCI    => ImageFormat.OCI
    }

    try {
      val containerizer = Containerizer
        .to(configuration.targetImageFactory(jibTargetImageCredentialHelper))
        .setToolName(USER_AGENT_SUFFIX)
        .setApplicationLayersCache(Files.createTempDirectory("jib-application-layer-cache"))
        .setBaseImageLayersCache(Files.createTempDirectory("jib-base-image-layer-cache"))

      Jib
        .from(configuration.baseImageFactory(jibBaseImageCredentialHelper))
        .setLayers(configuration.getLayerConfigurations)
        .setEnvironment(environment.asJava)
        .setProgramArguments(args.asJava)
        .setFormat(internalImageFormat)
        .setEntrypoint(configuration.entrypoint(jvmFlags))
        .containerize(containerizer)

      logger.success("image successfully created & uploaded")
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create image (Exception: $t)")
    }
  }

}
