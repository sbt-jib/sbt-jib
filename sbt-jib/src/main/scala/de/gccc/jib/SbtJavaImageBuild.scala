package de.gccc.jib

import com.google.cloud.tools.jib.api.Containerizer
import com.google.cloud.tools.jib.api.buildplan.*
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import de.gccc.jib.common.JibCommon
import sbt.internal.util.ManagedLogger
import xsbti.FileConverter

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
  )(implicit converter: FileConverter): Unit =
    try {
      val targetImage = JibCommon.targetImageFactory(configuration.targetImageReference)(
        jibTargetImageCredentialHelper,
        configuration.credsForHost,
        configuration.logEvent
      )
      val containerizer = Containerizer.to(targetImage)
      SbtJibHelper.javaBuild(
        targetDirectory,
        configuration,
        jibBaseImageCredentialHelper,
        jvmFlags,
        tcpPorts,
        udpPorts,
        args,
        imageFormat,
        environment,
        labels,
        additionalTags,
        user,
        useCurrentTimestamp,
        platforms
      )(containerizer)
      logger.success("java image successfully created & uploaded")
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create java image (Exception: $t)")
        throw t
    }

}
