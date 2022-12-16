package de.gccc.jib

import java.io.File
import com.google.cloud.tools.jib.api.buildplan.{ AbsoluteUnixPath, FileEntriesLayer, ImageFormat, Platform, Port }
import com.google.cloud.tools.jib.api.{ Containerizer, ImageReference, JavaContainerBuilder, JibContainer }
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.internal.util.ManagedLogger

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

private[jib] object SbtJibHelper {

  def mappingsConverter(name: String, mappings: Seq[(File, String)]): FileEntriesLayer = {
    val layerBuilder = FileEntriesLayer.builder()

    mappings
      .filter(_._1.isFile) // fixme resolve all directory files
      .map { case (file, fullPathOnImage) => (file.toPath, fullPathOnImage) }
      .toList
      .sortBy(_._2)
      .foreach { case (sourceFile, pathOnImage) =>
        layerBuilder.addEntry(sourceFile, AbsoluteUnixPath.get(pathOnImage))
      }

    layerBuilder.build()
  }

  def javaBuild(
      targetDirectory: File,
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
  )(containerizer: Containerizer): ImageReference = {
    val internalImageFormat = imageFormat match {
      case JibImageFormat.Docker => ImageFormat.Docker
      case JibImageFormat.OCI    => ImageFormat.OCI
    }
    val baseImage = JibCommon.baseImageFactory(configuration.baseImageReference)(
      jibBaseImageCredentialHelper,
      configuration.credsForHost,
      _ => ()
    )
    val _ = JibCommon.configureContainerizer(containerizer)(
      additionalTags,
      configuration.allowInsecureRegistries,
      configuration.USER_AGENT_SUFFIX,
      configuration.target
    )
    val builder = JibCommon
      .prepareJavaContainerBuilder(JavaContainerBuilder.from(baseImage))(
        configuration.layerConfigurations,
        Some(configuration.pickedMainClass),
        jvmFlags,
        logger.warn
      )
      .toContainerBuilder
    val container = JibCommon
      .prepareJibContainerBuilder(builder)(
        tcpPorts.toSet.map(s => Port.tcp(s)) ++ udpPorts.toSet.map(s => Port.udp(s)),
        args,
        internalImageFormat,
        environment,
        labels,
        user,
        useCurrentTimestamp,
        platforms
      )
      .containerize(containerizer)

    JibCommon.writeJibOutputFiles(container)(targetDirectory)

    configuration.targetImageReference
  }
}
