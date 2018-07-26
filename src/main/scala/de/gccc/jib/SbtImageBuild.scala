package de.gccc.jib

import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException
import com.google.cloud.tools.jib.configuration.BuildConfiguration
import com.google.cloud.tools.jib.frontend.{ BuildStepsExecutionException, BuildStepsRunner, JavaEntrypointConstructor }
import com.google.cloud.tools.jib.image.ImageFormat
import com.google.cloud.tools.jib.registry.RegistryClient
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat

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
      environment: Map[String, String]
  ): Unit = {

    val internalImageFormat = imageFormat match {
      case JibImageFormat.Docker => ImageFormat.Docker
      case JibImageFormat.OCI    => ImageFormat.OCI
    }

    val targetImage = configuration.targetImageReference

    val buildConfiguration = BuildConfiguration
      .builder(configuration.getLogger)
      .setBaseImage(configuration.baseImageReference)
      .setBaseImageCredentialHelperName(jibBaseImageCredentialHelper.orNull)
      .setKnownBaseRegistryCredentials(configuration.baseImageCredentials.orNull)
      .setTargetImage(targetImage)
      .setTargetImageCredentialHelperName(jibTargetImageCredentialHelper.orNull)
      .setKnownTargetRegistryCredentials(configuration.targetImageCredentials.orNull)
      .setJavaArguments(args.asJava)
      .setEnvironment(environment.asJava)
      .setTargetFormat(internalImageFormat.getManifestTemplateClass)
      .setEntrypoint(
        JavaEntrypointConstructor.makeDefaultEntrypoint(jvmFlags.asJava, configuration.getMainClassFromJar)
      )
      .setLayerConfigurations(configuration.getLayerConfigurations)
      .build()

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX)

    try {
      BuildStepsRunner.forBuildImage(buildConfiguration).build(HELPFUL_SUGGESTIONS)

      configuration.getLogger.info("")
    } catch {
      case e @ (_: CacheDirectoryCreationException | _: BuildStepsExecutionException) =>
        throw new Exception(e.getMessage, e.getCause)
    }
  }

}
