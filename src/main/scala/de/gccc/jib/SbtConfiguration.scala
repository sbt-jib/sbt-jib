package de.gccc.jib

import java.io.File
import java.nio.file.{ Files, Path }

import com.google.cloud.tools.jib.builder.{ BuildLogger, SourceFilesConfiguration }
import com.google.cloud.tools.jib.frontend.{ HelpfulSuggestions, ProjectProperties }
import com.google.cloud.tools.jib.http.Authorizations
import com.google.cloud.tools.jib.image.ImageReference
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials
import sbt.librarymanagement.ivy.{ Credentials, DirectCredentials }
import sbt.util.Logger

private[jib] class SbtConfiguration(
    logger: Logger,
    sourceFileConfiguration: SbtSourceFilesConfiguration,
    mainClass: Option[String],
    targetValue: File,
    credentials: Seq[Credentials],
    val baseImageReference: ImageReference,
    val registry: String,
    val organization: String,
    val name: String,
    val version: String
) extends ProjectProperties {

  private def generateCredentials(sbtCreds: Option[DirectCredentials], usernameEnv: String, passwdEnv: String) = {
    sbtCreds.orElse {
      val usernameOption = sys.env.get(usernameEnv)
      val passwordOption = sys.env.get(passwdEnv)
      for {
        username <- usernameOption
        password <- passwordOption
      } yield new DirectCredentials("", "", username, password)
    }.map { sbtCredentials =>
      new RegistryCredentials(
        "sbt",
        Authorizations.withBasicCredentials(sbtCredentials.userName, sbtCredentials.passwd)
      )
    }
  }

  private val PLUGIN_NAME     = "jib-sbt-plugin"
  private val JAR_PLUGIN_NAME = "'sbt-jar-plugin'"

  override lazy val getLogger: BuildLogger = new SbtBuildLogger(logger)

  override def getPluginName: String = PLUGIN_NAME

  override def getSourceFilesConfiguration: SourceFilesConfiguration = sourceFileConfiguration

  override def getCacheDirectory: Path = {
    val targetPath = targetValue.toPath
    if (Files.notExists(targetPath)) {
      Files.createDirectories(targetPath)
    }
    targetPath
  }

  override def getJarPluginName: String = JAR_PLUGIN_NAME

  /** @return the name of the main class configured in a jar plugin, or null if none is found. */
  override def getMainClassFromJar: String = mainClass.orNull

  /**
   * @param prefix the prefix message for the { @link HelpfulSuggestions}.
   * @return a { @link HelpfulSuggestions} instance for main class inference failure.
   */
  override def getMainClassHelpfulSuggestions(prefix: String): HelpfulSuggestions = {
    SbtConfiguration.helpfulSuggestionProvider(prefix)
  }

  lazy val targetImageReference: ImageReference = {
    // TODO: actually organization is probably not a good idea to use
    // so we should add a jibOrganization and/or jibName to overwrite the project defaults if they might differ
    val repository = organization + "/" + name
    ImageReference.of(registry, repository, version)
  }

  lazy val baseImageCredentials: Option[RegistryCredentials] = {
    generateCredentials(
      credentials.collectFirst { case d: DirectCredentials if d.host == baseImageReference.getRegistry => d },
      "JIB_BASE_IMAGE_USERNAME",
      "JIB_BASE_IAMGE_PASSWORD"
    )
  }

  lazy val targetImageCredentials: Option[RegistryCredentials] = {
    generateCredentials(
      credentials.collectFirst { case d: DirectCredentials if d.host == targetImageReference.getRegistry => d },
      "JIB_TARGET_IAMGE_USERNAME",
      "JIB_TARGET_IMAGE_PASSWORD"
    )
  }

}

private[jib] object SbtConfiguration {

  def helpfulSuggestionProvider(messagePrefix: String): HelpfulSuggestions = {
    new HelpfulSuggestions(
      messagePrefix,
      "sbt clean",
      "from.credHelper",
      ignored => "from.auth",
      "to.credHelper",
      ignored => "to.auth"
    )
  }

}
