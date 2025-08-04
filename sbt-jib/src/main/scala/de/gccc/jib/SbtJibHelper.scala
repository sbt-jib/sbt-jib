package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan._
import com.google.cloud.tools.jib.api.{ Containerizer, DockerDaemonImage, JavaContainerBuilder }
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import de.gccc.jib.common.JibCommon
import sbt.io.Path
import sbt.nio.file.Glob

import java.io.File

private[jib] object SbtJibHelper {

  def mappingsConverter(
      name: String,
      mappings: Seq[(File, String)],
      permissionsForMapping: Seq[(Glob, String)] = Nil
  ): FileEntriesLayer = {
    val layerBuilder = FileEntriesLayer.builder()
    val permissions = permissionsForMapping.map { case (glob, permission) =>
      (glob, FilePermissions.fromOctalString(permission))
    }
    def permissionOfPath(pathOnImage: String): Option[FilePermissions] = {
      permissions.find { case (glob, _) => glob.matches(Path(pathOnImage).asPath) }.map(_._2)
    }
    mappings
      .filter(_._1.isFile) // fixme resolve all directory files
      .map { case (file, fullPathOnImage) => (file.toPath, fullPathOnImage) }
      .toList
      .sortBy(_._2)
      .foreach {
        case (sourceFile, pathOnImage) if permissionOfPath(pathOnImage).isDefined =>
          layerBuilder.addEntry(sourceFile, AbsoluteUnixPath.get(pathOnImage), permissionOfPath(pathOnImage).get)
        case (sourceFile, pathOnImage) =>
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
    val javaBuilder =
      if (configuration.isDockerDaemonBase)
        JavaContainerBuilder.from(DockerDaemonImage.named(configuration.baseImageReference))
      else
        JavaContainerBuilder.from(
          JibCommon.baseImageFactory(configuration.baseImageReference)(
            jibBaseImageCredentialHelper,
            configuration.credsForHost,
            configuration.logEvent
          )
        )
    JibCommon.configureContainerizer(containerizer)(
      additionalTags,
      configuration.allowInsecureRegistries,
      configuration.USER_AGENT_SUFFIX,
      configuration.target.toPath
    )
    JibCommon.prepareJavaContainerBuilder(javaBuilder)(
      configuration.layerConfigurations.external.map(_.data.toPath).toList,
      configuration.layerConfigurations.addToClasspath.map(_.toPath),
      configuration.layerConfigurations.internalDependencies.map(_.data.toPath).toList,
      configuration.layerConfigurations.resourceDirectories.map(_.toPath).toList,
      configuration.layerConfigurations.classes.map(_.toPath).toList,
      Some(configuration.pickedMainClass),
      jvmFlags
    )
    val jibBuilder = javaBuilder.toContainerBuilder
    JibCommon.prepareJibContainerBuilder(jibBuilder)(
      tcpPorts.toSet.map(s => Port.tcp(s)) ++ udpPorts.toSet.map(s => Port.udp(s)),
      args,
      internalImageFormat,
      environment,
      labels,
      user,
      useCurrentTimestamp,
      platforms,
      None
    )
    val container = jibBuilder.containerize(containerizer)

    JibCommon.writeJibOutputFiles(container)(targetDirectory.toPath)
  }
}
