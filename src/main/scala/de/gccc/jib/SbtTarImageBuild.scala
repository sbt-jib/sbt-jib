package de.gccc.jib

import com.google.cloud.tools.jib.api.{Containerizer, ImageReference, Jib, TarImage}
import com.google.cloud.tools.jib.api.buildplan.ImageFormat
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.internal.util.ManagedLogger

import java.io.File
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private[jib] object SbtTarImageBuild {

  private val USER_AGENT_SUFFIX = "jib-sbt-plugin"

  def task(
      home: sbt.File,
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
  ): Unit = {
    val internalImageFormat = imageFormat match {
      case JibImageFormat.Docker => ImageFormat.Docker
      case JibImageFormat.OCI    => ImageFormat.OCI
    }

    try {
      val imageReference = ImageReference.of(configuration.registry, configuration.repository, configuration.version)

      val image = TarImage.at(home.toPath).named(imageReference)

      val containerizer = Containerizer
        .to(image)
        .setToolName(USER_AGENT_SUFFIX)
        .setApplicationLayersCache(target.toPath.resolve("application-layer-cache"))
        .setBaseImageLayersCache(target.toPath.resolve("base-image-layer-cache"))

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
        .containerize(containerizer)

      logger.success("image successfully created & uploaded")
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create tar image (Exception: $t)")
        throw t
    }
  }

}
