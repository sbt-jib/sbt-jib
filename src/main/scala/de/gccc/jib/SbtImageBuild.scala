package de.gccc.jib

import com.google.cloud.tools.jib.api.{ Containerizer, ImageReference, Jib }
import com.google.cloud.tools.jib.api.buildplan.{ ImageFormat, Platform, Port }
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.internal.util.ManagedLogger

import java.io.File
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private[jib] object SbtImageBuild {

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
      entrypoint: Option[List[String]],
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
      val targetImage = JibCommon.targetImageFactory(configuration.targetImageReference)(
        jibTargetImageCredentialHelper,
        configuration.credsForHost,
        configuration.logEvent
      )
      val taggedImage = Containerizer.to(targetImage)
      JibCommon.configureContainerizer(taggedImage)(
        additionalTags,
        configuration.allowInsecureRegistries,
        configuration.USER_AGENT_SUFFIX,
        targetDirectory
      )
      val baseImage = JibCommon.baseImageFactory(configuration.baseImageReference)(
        jibBaseImageCredentialHelper,
        configuration.credsForHost,
        configuration.logEvent
      )
      val container = Jib
        .from(baseImage)
        .setFileEntriesLayers(configuration.getLayerConfigurations)
        .setEnvironment(environment.asJava)
        .setPlatforms(platforms.asJava)
        .setLabels(labels.asJava)
        .setUser(user.orNull)
        .setProgramArguments(args.asJava)
        .setFormat(internalImageFormat)
        .setEntrypoint(configuration.entrypoint(jvmFlags, entrypoint))
        .setExposedPorts((tcpPorts.toSet.map(s => Port.tcp(s)) ++ udpPorts.toSet.map(s => Port.udp(s))).asJava)
        .setCreationTime(TimestampHelper.useCurrentTimestamp(useCurrentTimestamp))
        .containerize(taggedImage)

      JibCommon.writeJibOutputFiles(container)(targetDirectory)

      logger.success("image successfully created & uploaded")
      configuration.targetImageReference
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create image (Exception: $t)")
        throw t
    }
  }

}
