package de.gccc.jib

import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException
import com.google.cloud.tools.jib.configuration.{ BuildConfiguration, CacheConfiguration }
import com.google.cloud.tools.jib.docker.DockerClient
import com.google.cloud.tools.jib.frontend.{ BuildStepsExecutionException, BuildStepsRunner, JavaEntrypointConstructor }
import com.google.cloud.tools.jib.image.ImageReference
import com.google.cloud.tools.jib.registry.RegistryClient

import scala.collection.JavaConverters._

private[jib] object SbtDockerBuild {

  private val USER_AGENT_SUFFIX = "jib-sbt-plugin"

  def task(
      configuration: SbtConfiguration,
      jibBaseImageCredentialHelper: Option[String],
      defaultImage: String,
      jvmFlags: List[String],
      args: List[String]
  ): Unit = {
    val HELPFUL_SUGGESTIONS = SbtConfiguration.helpfulSuggestionProvider("Build to Docker daemon failed")

    if (!new DockerClient().isDockerInstalled) {
      throw new Exception(HELPFUL_SUGGESTIONS.forDockerNotInstalled())
    }

    val baseImageReference = ImageReference.parse(defaultImage)

    val buildLogger = configuration.getLogger

    val buildConfiguration =
      BuildConfiguration
        .builder(buildLogger)
        .setBaseImage(baseImageReference)
        .setTargetImage(configuration.targetImageReference)
        .setBaseImageCredentialHelperName(jibBaseImageCredentialHelper.orNull)
        .setKnownBaseRegistryCredentials(configuration.baseImageCredentials.orNull)
        .setJavaArguments(args.asJava)
        .setEntrypoint(
          JavaEntrypointConstructor.makeDefaultEntrypoint(jvmFlags.asJava, configuration.getMainClassFromJar)
        )
        .setLayerConfigurations(configuration.getLayerConfigurations)
        .setBaseImageLayersCacheConfiguration(CacheConfiguration.makeTemporary())
        .setApplicationLayersCacheConfiguration(CacheConfiguration.makeTemporary())
        .build()

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX)

    try {
      BuildStepsRunner.forBuildToDockerDaemon(buildConfiguration).build(HELPFUL_SUGGESTIONS)
    } catch {
      case e @ (_: CacheDirectoryCreationException | _: BuildStepsExecutionException) =>
        throw new Exception(e.getMessage, e.getCause)
    }
  }

}
