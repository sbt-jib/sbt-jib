package de.gccc.jib

import com.google.cloud.tools.jib.api.{ Containerizer, Jib }
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException
import com.google.cloud.tools.jib.image.ImageFormat
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat

import scala.collection.JavaConverters._

private[jib] object SbtImageBuild {

  private val USER_AGENT_SUFFIX = "jib-sbt-plugin"

  def task(
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

      jib
        .setEnvironment(environment.asJava)
        .setProgramArguments(args.asJava)
        .setFormat(internalImageFormat)
        .setEntrypoint(configuration.entrypoint(jvmFlags))
        .containerize(
          Containerizer
            .to(configuration.targetImageFactory(jibTargetImageCredentialHelper))
            .setToolName(USER_AGENT_SUFFIX)
            // .setBaseImageLayersCache()
            // .setApplicationLayersCache()
        )

    } catch {
      case e @ (_: CacheDirectoryCreationException) =>
        throw new Exception(e.getMessage, e.getCause)
    }
  }

}
