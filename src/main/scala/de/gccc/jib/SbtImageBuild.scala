package de.gccc.jib

import com.google.cloud.tools.jib.builder.BuildConfiguration
import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException
import com.google.cloud.tools.jib.configuration.LayerConfiguration
import com.google.cloud.tools.jib.frontend.{ BuildStepsExecutionException, BuildStepsRunner }
import com.google.cloud.tools.jib.image.ImageFormat
import com.google.cloud.tools.jib.registry.RegistryClient
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.File

import scala.collection.JavaConverters._

private[jib] object SbtImageBuild {

  private val USER_AGENT_SUFFIX = "jib-sbt-plugin"

  private val HELPFUL_SUGGESTIONS = SbtConfiguration.helpfulSuggestionProvider("Build image failed")

  def task(
      configuration: SbtConfiguration,
      jibBaseImageCredentialHelper: Option[String],
      jibTargetImageCredentialHelper: Option[String],
      jvmFlags: List[String],
      args: List[String],
      imageFormat: JibImageFormat,
      environment: Map[String, String],
      mappings: Seq[(File, String)]
  ): Unit = {

    val internalImageFormat = imageFormat match {
      case JibImageFormat.Docker => ImageFormat.Docker
      case JibImageFormat.OCI    => ImageFormat.OCI
    }

    val targetImage = configuration.targetImageReference

    val extraLayer = if (mappings.nonEmpty) SbtJibHelper.mappingsConverter(mappings) else null

    val buildConfiguration = BuildConfiguration
      .builder(configuration.getLogger)
      .setBaseImage(configuration.baseImageReference)
      .setBaseImageCredentialHelperName(jibBaseImageCredentialHelper.orNull)
      .setKnownBaseRegistryCredentials(configuration.baseImageCredentials.orNull)
      .setTargetImage(targetImage)
      .setTargetImageCredentialHelperName(jibTargetImageCredentialHelper.orNull)
      .setKnownTargetRegistryCredentials(configuration.targetImageCredentials.orNull)
      .setMainClass(configuration.getMainClassFromJar)
      .setJavaArguments(args.asJava)
      .setJvmFlags(jvmFlags.asJava)
      .setEnvironment(environment.asJava)
      .setTargetFormat(internalImageFormat.getManifestTemplateClass)
      .setExtraFilesLayerConfiguration(extraLayer)
      .build()

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX)

    try {
      BuildStepsRunner
        .forBuildImage(
          buildConfiguration,
          configuration.getSourceFilesConfiguration
        )
        .build(HELPFUL_SUGGESTIONS)

      configuration.getLogger.info("")
    } catch {
      case e @ (_: CacheDirectoryCreationException | _: BuildStepsExecutionException) =>
        throw new Exception(e.getMessage, e.getCause)
    }
  }

}
