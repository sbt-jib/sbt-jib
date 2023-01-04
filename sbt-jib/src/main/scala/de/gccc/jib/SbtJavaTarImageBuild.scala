package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan._
import com.google.cloud.tools.jib.api._
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.internal.util.ManagedLogger

import java.io.File
import scala.util.control.NonFatal

private[jib] object SbtJavaTarImageBuild {
  def task(
      targetDirectory: File,
      home: sbt.File,
      logger: ManagedLogger,
      configuration: SbtConfiguration,
      jibBaseImageCredentialHelper: Option[String],
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
  ): Unit =
    try {
      val targetImage   = TarImage.at(home.toPath).named(configuration.targetImageReference)
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
        logger.error(s"could not create java tar image (Exception: $t)")
        throw t
    }

}
