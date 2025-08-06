package de.gccc.jib

import com.google.cloud.tools.jib.api.{ Containerizer, DockerDaemonImage, ImageReference, Jib, TarImage }
import com.google.cloud.tools.jib.api.buildplan.{ ImageFormat, Port }
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import de.gccc.jib.common.JibCommon
import sbt.internal.util.ManagedLogger

import java.io.File
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

private[jib] object SbtTarImageBuild {
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
      entrypoint: Option[List[String]],
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
      val taggedImage = Containerizer.to(targetImage)
      JibCommon.configureContainerizer(taggedImage)(
        additionalTags,
        configuration.allowInsecureRegistries,
        configuration.USER_AGENT_SUFFIX,
        targetDirectory.toPath
      )
      val jibBuilder =
        if (configuration.isDockerDaemonBase)
          Jib.from(DockerDaemonImage.named(configuration.baseImageReference))
        else
          Jib.from(
            JibCommon.baseImageFactory(configuration.baseImageReference)(
              jibBaseImageCredentialHelper,
              configuration.credsForHost,
              configuration.logEvent
            )
          )
      val container = jibBuilder
        .setFileEntriesLayers(configuration.getLayerConfigurations)
        .setEnvironment(environment.asJava)
        .setLabels(labels.asJava)
        .setUser(user.orNull)
        .setProgramArguments(args.asJava)
        .setFormat(internalImageFormat)
        .setEntrypoint(configuration.entrypoint(jvmFlags, entrypoint))
        .setExposedPorts((tcpPorts.toSet.map(s => Port.tcp(s)) ++ udpPorts.toSet.map(s => Port.udp(s))).asJava)
        .setCreationTime(JibCommon.useCurrentTimestamp(useCurrentTimestamp))
        .containerize(taggedImage)

      JibCommon.writeJibOutputFiles(container)(targetDirectory.toPath)

      logger.success("image successfully created & uploaded")
    } catch {
      case NonFatal(t) =>
        logger.error(s"could not create tar image (Exception: $t)")
        throw t
    }
  }

}
