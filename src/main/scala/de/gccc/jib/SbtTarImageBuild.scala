package de.gccc.jib

import com.google.cloud.tools.jib.api.{ Containerizer, Jib, TarImage }
import com.google.cloud.tools.jib.image.{ ImageFormat, ImageReference }
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.internal.util.ManagedLogger

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
      imageFormat: JibImageFormat,
      environment: Map[String, String]
  ): Unit = {

    val internalImageFormat = imageFormat match {
      case JibImageFormat.Docker => ImageFormat.Docker
      case JibImageFormat.OCI    => ImageFormat.OCI
    }

    try {
      val jib = Jib.from(configuration.baseImageFactory(jibBaseImageCredentialHelper))

      configuration.getLayerConfigurations.forEach { configuration =>
        jib.addLayer(configuration)
      }

      var imageReference = ImageReference.of(configuration.registry,
                                             configuration.organization + "/" + configuration.name,
                                             configuration.version)

      val image         = TarImage.named(imageReference).saveTo(home.toPath)
      val containerizer = Containerizer.to(image).setToolName(USER_AGENT_SUFFIX)

      jib
        .setEnvironment(environment.asJava)
        .setProgramArguments(args.asJava)
        .setFormat(internalImageFormat)
        .setEntrypoint(configuration.entrypoint(jvmFlags))
        .containerize(containerizer)

      logger.info("image successfully created & uploaded")
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create tar image (Exception: $t)")
    }
  }

}
