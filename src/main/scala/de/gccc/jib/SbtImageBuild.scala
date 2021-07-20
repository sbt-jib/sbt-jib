package de.gccc.jib

import com.google.cloud.tools.jib.api.{Containerizer, ImageReference, Jib}
import com.google.cloud.tools.jib.api.buildplan.ImageFormat
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.internal.util.ManagedLogger

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private[jib] object SbtImageBuild {

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
  ): ImageReference = {

    val internalImageFormat = imageFormat match {
      case JibImageFormat.Docker => ImageFormat.Docker
      case JibImageFormat.OCI    => ImageFormat.OCI
    }

    try {
      val targetImage = configuration.targetImageFactory(jibTargetImageCredentialHelper)

      Jib
        .from(configuration.baseImageFactory(jibBaseImageCredentialHelper))
        .setFileEntriesLayers(configuration.getLayerConfigurations)
        .setEnvironment(environment.asJava)
        .setLabels(labels.asJava)
        .setUser(user.orNull)
        .setProgramArguments(args.asJava)
        .setFormat(internalImageFormat)
        .setEntrypoint(configuration.entrypoint(jvmFlags, entrypoint))
        .setCreationTime(TimestampHelper.useCurrentTimestamp(useCurrentTimestamp))
        .containerize(configuration.configureContainerizer(Containerizer.to(targetImage)))

      logger.success("image successfully created & uploaded")
      configuration.targetImageReference
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create image (Exception: $t)")
        throw t
    }
  }

}
