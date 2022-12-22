package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan._
import com.google.cloud.tools.jib.api.{ Containerizer, JavaContainerBuilder }
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import de.gccc.jib.common.JibCommon

import java.io.File

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
  )(containerizer: Containerizer): Unit = {
    val internalImageFormat = imageFormat match {
      case JibImageFormat.Docker => ImageFormat.Docker
      case JibImageFormat.OCI    => ImageFormat.OCI
    }
    val baseImage = JibCommon.baseImageFactory(configuration.baseImageReference)(
      jibBaseImageCredentialHelper,
      configuration.credsForHost,
      configuration.logEvent
    )
    JibCommon.configureContainerizer(containerizer)(
      additionalTags,
      configuration.allowInsecureRegistries,
      configuration.USER_AGENT_SUFFIX,
      configuration.target.toPath
    )
    val builder = JavaContainerBuilder.from(baseImage)
    JibCommon.prepareJavaContainerBuilder(builder)(
      configuration.layerConfigurations.external.map(_.data.toPath).toList,
      configuration.layerConfigurations.addToClasspath.map(_.toPath),
      configuration.layerConfigurations.internalDependencies.map(_.data.toPath).toList,
      configuration.layerConfigurations.resourceDirectories.map(_.toPath).toList,
      configuration.layerConfigurations.classes.map(_.toPath).toList,
      Some(configuration.pickedMainClass),
      jvmFlags
    )
    val container = JibCommon
      .prepareJibContainerBuilder(builder.toContainerBuilder)(
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

    JibCommon.writeJibOutputFiles(container)(targetDirectory.toPath)
  }
}
