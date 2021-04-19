package de.gccc.jib

import com.google.cloud.tools.jib.api.{Containerizer, ImageReference, Jib}
import com.google.cloud.tools.jib.api.buildplan.ImageFormat
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.internal.util.ManagedLogger

import java.io.File
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
      entrypoint: Option[List[String]],
      imageFormat: JibImageFormat,
      environment: Map[String, String],
      labels: Map[String, String],
      user: Option[String],
      useCurrentTimestamp: Boolean,
      target: File,
  ): ImageReference = {

    val internalImageFormat = imageFormat match {
      case JibImageFormat.Docker => ImageFormat.Docker
      case JibImageFormat.OCI    => ImageFormat.OCI
    }

    try {
      val containerizer = Containerizer
        .to(configuration.targetImageFactory(jibTargetImageCredentialHelper))
        .setToolName(USER_AGENT_SUFFIX)
        .setApplicationLayersCache(target.toPath.resolve("application-layer-cache"))
        .setBaseImageLayersCache(target.toPath.resolve("base-image-layer-cache"))

      Jib
        .from(configuration.baseImageFactory(jibBaseImageCredentialHelper))
        .setLayers(configuration.getLayerConfigurations)
        .setEnvironment(environment.asJava)
        .setLabels(labels.asJava)
        .setUser(user.orNull)
        .setProgramArguments(args.asJava)
        .setFormat(internalImageFormat)
        .setEntrypoint(configuration.entrypoint(jvmFlags, entrypoint))
        .setCreationTime(TimestampHelper.useCurrentTimestamp(useCurrentTimestamp))
        .containerize(containerizer)

      logger.success("image successfully created & uploaded")
      return configuration.targetImageReference
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create image (Exception: $t)")
        throw t
    }
  }

}
