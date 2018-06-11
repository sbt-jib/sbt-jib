package de.gccc.jib

import com.google.cloud.tools.jib.builder.BuildConfiguration
import com.google.cloud.tools.jib.frontend.{
  BuildStepsExecutionException,
  BuildStepsRunner,
  CacheDirectoryCreationException
}
import com.google.cloud.tools.jib.http.{ Authorization, Authorizations }
import com.google.cloud.tools.jib.image.{ ImageFormat, ImageReference }
import com.google.cloud.tools.jib.registry.RegistryClient
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials
import de.gccc.jib.JibPlugin.autoImport.JibImageFormat
import sbt.DirectCredentials

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
      imageFormat: JibImageFormat
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
      .setMainClass(configuration.getMainClassFromJar)
      .setJavaArguments(args.asJava)
      .setJvmFlags(jvmFlags.asJava)
      // .setEnvironment() // FIXME: this adds environment variables to jib
      .setTargetFormat(internalImageFormat.getManifestTemplateClass)
      .build()

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX)

    try {
      BuildStepsRunner
        .forBuildImage(
          buildConfiguration,
          configuration.getSourceFilesConfiguration,
          configuration.getCacheDirectory,
          true // sbt does not have a shared cache folder
        )
        .build(HELPFUL_SUGGESTIONS)

      configuration.getLogger.info("")
    } catch {
      case e @ (_: CacheDirectoryCreationException | _: BuildStepsExecutionException) =>
        throw new Exception(e.getMessage, e.getCause)
    }
  }

}
