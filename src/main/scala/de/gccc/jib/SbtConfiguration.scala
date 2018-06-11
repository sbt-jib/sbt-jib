package de.gccc.jib

import java.io.File
import java.nio.file.Path

import com.google.cloud.tools.jib.builder.{ BuildLogger, SourceFilesConfiguration }
import com.google.cloud.tools.jib.frontend.{ HelpfulSuggestions, ProjectProperties }
import sbt.util.Logger

class SbtConfiguration(
    logger: Logger,
    sourceFileConfiguration: SbtSourceFilesConfiguration,
    mainClass: Option[String],
    targetValue: File,
    val organization: String,
    val name: String,
    val version: String
) extends ProjectProperties {

  private val PLUGIN_NAME = "jib-sbt-plugin"
  private val JAR_PLUGIN_NAME = "'sbt-jar-plugin'"

  override lazy val getLogger: BuildLogger = new SbtBuildLogger(logger)

  override def getPluginName: String = PLUGIN_NAME

  override def getSourceFilesConfiguration: SourceFilesConfiguration = sourceFileConfiguration

  override def getCacheDirectory: Path = targetValue.toPath

  override def getJarPluginName: String = JAR_PLUGIN_NAME

  /** @return the name of the main class configured in a jar plugin, or null if none is found. */
  override def getMainClassFromJar: String = mainClass.orNull

  /**
   * @param prefix the prefix message for the { @link HelpfulSuggestions}.
   * @return a { @link HelpfulSuggestions} instance for main class inference failure.
   */
  override def getMainClassHelpfulSuggestions(prefix: String): HelpfulSuggestions = ???

}
