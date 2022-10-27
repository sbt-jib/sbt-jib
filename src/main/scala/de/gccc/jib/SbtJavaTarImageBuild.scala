package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan.ImageFormat
import com.google.cloud.tools.jib.api.{ Containerizer, ImageReference, JavaContainerBuilder, TarImage }
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.internal.util.ManagedLogger

import java.io.File
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private[jib] object SbtJavaTarImageBuild {
  def task(
      targetDirectory: File,
      home: sbt.File,
      logger: ManagedLogger,
      configuration: SbtConfiguration,
      jibBaseImageCredentialHelper: Option[String],
      jvmFlags: List[String],
      args: List[String],
      imageFormat: JibImageFormat,
      environment: Map[String, String],
      labels: Map[String, String],
      additionalTags: List[String],
      user: Option[String],
      useCurrentTimestamp: Boolean
  ): Unit = {
    val internalImageFormat = imageFormat match {
      case JibImageFormat.Docker => ImageFormat.Docker
      case JibImageFormat.OCI    => ImageFormat.OCI
    }

    try {
      val imageReference = ImageReference.of(configuration.registry, configuration.repository, configuration.version)

      val targetImage = TarImage.at(home.toPath).named(imageReference)
      val taggedImage =
        additionalTags.foldRight(Containerizer.to(targetImage))((tag, image) => image.withAdditionalTag(tag))

      val container = new SbtJavaCommon(
        JavaContainerBuilder.from(configuration.baseImageFactory(jibBaseImageCredentialHelper)),
        logger
      ).prepareJavaContainerBuilder(
        configuration.layerConfigurations,
        configuration.pickedMainClass,
        jvmFlags
      ).toContainerBuilder
        .setEnvironment(environment.asJava)
        .setLabels(labels.asJava)
        .setUser(user.orNull)
        .setProgramArguments(args.asJava)
        .setFormat(internalImageFormat)
        .setCreationTime(TimestampHelper.useCurrentTimestamp(useCurrentTimestamp))
        .containerize(configuration.configureContainerizer(taggedImage))

      SbtJibHelper.writeJibOutputFiles(targetDirectory, container)

      logger.success("java image successfully created & uploaded")
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create java tar image (Exception: $t)")
        throw t
    }
  }

}
