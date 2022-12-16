package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan.{ AbsoluteUnixPath, FileEntriesLayer }
import com.google.cloud.tools.jib.api.{
  Containerizer,
  Credential,
  CredentialRetriever,
  ImageReference,
  LogEvent,
  RegistryImage
}
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory
import com.google.cloud.tools.jib.global.JibSystemProperties
import com.google.common.collect.ImmutableList
import sbt.librarymanagement.ivy.Credentials
import sbt.util.Logger

import java.io.File
import java.nio.file.{ Files, Path }
import java.util.Optional
import scala.collection.JavaConverters._

private[jib] class SbtConfiguration(
    logger: Logger,
    val layerConfigurations: SbtLayerConfigurations,
    mainClass: Option[String],
    discoveredMainClasses: Seq[String],
    targetValue: File,
    val credentials: Seq[Credentials],
    val baseImageReference: ImageReference,
    val registry: String,
    val organization: String,
    val name: String,
    val version: String,
    val customRepositoryPath: Option[String],
    val allowInsecureRegistries: Boolean,
    val sendCredentialsOverHttp: Boolean,
    val target: File
) {

  val USER_AGENT_SUFFIX = "jib-sbt-plugin"

  JibCommon.setSendCredentialsOverHttp(sendCredentialsOverHttp)

  val repository: String = customRepositoryPath.getOrElse(organization + "/" + name)

  private val PLUGIN_NAME     = "jib-sbt-plugin"
  private val JAR_PLUGIN_NAME = "'sbt-jar-plugin'"

  def getPluginName: String = PLUGIN_NAME

  def getLayerConfigurations: ImmutableList[FileEntriesLayer] = {
    ImmutableList.copyOf[FileEntriesLayer](layerConfigurations.generate.asJavaCollection)
  }

  def getCacheDirectory: Path = {
    val targetPath = targetValue.toPath
    if (Files.notExists(targetPath)) {
      Files.createDirectories(targetPath)
    }
    targetPath
  }

  def getJarPluginName: String = JAR_PLUGIN_NAME

  lazy val targetImageReference: ImageReference =
    ImageReference.of(registry, repository, version)

  val credsForHost: String => Option[(String, String)] =
    Credentials.forHost(credentials, _).map(c => (c.userName, c.passwd))

  lazy val pickedMainClass: String = mainClass.getOrElse {
    discoveredMainClasses.toList match {
      case one :: Nil => one
      case first :: _ =>
        logger.warn(
          s"using first discovered main class for entrypoint ($first) this may not be what you want. Use the mainClass setting to specify the one you want."
        )
        first
      case Nil =>
        sys.error("no main class found for container image entrypoint")
    }
  }

  def entrypoint(jvmFlags: List[String], entrypoint: Option[List[String]]): java.util.List[String] = {
    entrypoint match {
      case Some(list) => list.asJava
      case None =>
        val appRoot = AbsoluteUnixPath.get("/app")
        JavaEntrypointConstructor.makeDefaultEntrypoint(appRoot, jvmFlags.asJava, pickedMainClass)
    }
  }

}
