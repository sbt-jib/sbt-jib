package de.gccc.jib

import com.google.cloud.tools.jib.builder.BuildConfiguration
import com.google.cloud.tools.jib.docker.DockerClient
import com.google.cloud.tools.jib.frontend.{
  BuildStepsExecutionException,
  BuildStepsRunner,
  CacheDirectoryCreationException,
  HelpfulSuggestions
}
import com.google.cloud.tools.jib.image.ImageReference
import com.google.cloud.tools.jib.registry.RegistryClient
import sbt._
import sbt.Keys._

import scala.collection.JavaConverters._

object JibPlugin extends AutoPlugin {

  private def helpfulSuggestionProvider(messagePrefix: String): HelpfulSuggestions = {
    new HelpfulSuggestions(
      messagePrefix,
      "sbt clean",
      "from.credHelper",
      ignored => "from.auth",
      "to.credHelper",
      ignored => "to.auth"
    )
  }

  private val USER_AGENT_SUFFIX   = "jib-sbt-plugin"
  private val HELPFUL_SUGGESTIONS = helpfulSuggestionProvider("Build to Docker daemon failed")

  object autoImport {
    sealed trait JibImageFormat
    object JibImageFormat {
      case object Docker extends JibImageFormat
      case object OCI    extends JibImageFormat
    }

    val jibBaseImage   = settingKey[String]("jib base image")
    val jibJvmFlags    = settingKey[List[String]]("jib default jvm flags")
    val jibArgs        = settingKey[List[String]]("jib default args")
    val jibImageFormat = settingKey[JibImageFormat]("jib default image format")
    val jibDockerBuild = taskKey[Unit]("jib build docker image")

    private[jib] object Private {
      val sbtSourceFilesConfiguration = {
        taskKey[SbtSourceFilesConfiguration]("jib source file settings")
      }
      val sbtConfiguration = taskKey[SbtConfiguration]("jib sbt configuration")
    }
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    // private values
    Private.sbtSourceFilesConfiguration := {
      val artifact = (artifactPath in (Compile, packageBin)).value.toPath
      val external = (externalDependencyClasspath or (externalDependencyClasspath in Runtime)).value
      val dependency = (internalDependencyAsJars in Compile).value
      new SbtSourceFilesConfiguration(
        artifact,
        dependency.map(_.data.toPath).toList,
        external.map(_.data.toPath).toList
      )
    },
    Private.sbtConfiguration := {
      new SbtConfiguration(
        sLog.value,
        Private.sbtSourceFilesConfiguration.value,
        (mainClass in (Compile, packageBin)).value,
        target.value / "jib",
        organization.value,
        name.value,
        version.value
      )
    },
    // public values
    jibBaseImage := "registry.hub.docker.com/schmitch/graalvm:latest",
    jibJvmFlags := Nil,
    jibArgs := Nil,
    jibImageFormat := JibImageFormat.Docker,
    jibDockerBuild := {
      val configuration = Private.sbtConfiguration.value
      val defaultImage  = jibBaseImage.value
      val jvmFlags      = jibJvmFlags.value
      val args          = jibArgs.value

      if (!new DockerClient().isDockerInstalled) {
        throw new Exception(HELPFUL_SUGGESTIONS.forDockerNotInstalled())
      }

      val baseImageReference = ImageReference.parse(defaultImage)

      // TODO: FIXME
      val repository           = configuration.organization + "/" + configuration.name
      val targetImageReference = ImageReference.of(null, repository, configuration.version)

      val buildLogger = configuration.getLogger

      val buildConfiguration =
        BuildConfiguration
          .builder(buildLogger)
          .setBaseImage(baseImageReference)
          .setTargetImage(targetImageReference)
//            .setBaseImageCredentialHelperName(jibExtension.getFrom().getCredHelper())
//            .setKnownBaseRegistryCredentials(knownBaseRegistryCredentials)
          .setMainClass(configuration.getMainClassFromJar)
          .setJavaArguments(args.asJava)
          .setJvmFlags(jvmFlags.asJava)
          .build()

      RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX)

      try {
        BuildStepsRunner
          .forBuildToDockerDaemon(
            buildConfiguration,
            configuration.getSourceFilesConfiguration,
            configuration.getCacheDirectory,
            false // jibExtension.getUseOnlyProjectCache()
          )
          .build(HELPFUL_SUGGESTIONS)
      } catch {
        case e @ (_: CacheDirectoryCreationException | _: BuildStepsExecutionException) =>
          throw new Exception(e.getMessage, e.getCause)
      }
    },
    jibDockerBuild := jibDockerBuild.dependsOn(packageBin in Compile).value
  )

}
