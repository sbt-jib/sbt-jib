package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan.{ ImageFormat, Platform }
import com.google.cloud.tools.jib.api.{ Containerizer, ImageReference }
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.internal.util.ManagedLogger

import java.io.File
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private[jib] object SbtJavaImageBuild {

  def task(
      targetDirectory: File,
      logger: ManagedLogger,
      configuration: SbtConfiguration,
      jibBaseImageCredentialHelper: Option[String],
      jibTargetImageCredentialHelper: Option[String],
      jvmFlags: List[String],
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

      val container = SbtJavaCommon
        .makeJibContainerBuilder(
          configuration.baseImageFactory(jibBaseImageCredentialHelper),
          configuration.layerConfigurations,
          configuration.pickedMainClass,
          jvmFlags,
          logger
        )
        .setEnvironment(environment.asJava)
        .setPlatforms(platforms.asJava)
        .setLabels(labels.asJava)
        .setUser(user.orNull)
        .setProgramArguments(args.asJava)
        .setFormat(internalImageFormat)
        .setCreationTime(TimestampHelper.useCurrentTimestamp(useCurrentTimestamp))
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
