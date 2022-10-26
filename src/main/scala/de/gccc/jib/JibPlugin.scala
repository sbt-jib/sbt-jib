package de.gccc.jib

import java.nio.file.Files
import com.google.cloud.tools.jib.api.ImageReference
import com.google.cloud.tools.jib.api.buildplan.{ FileEntriesLayer, Platform }
import sbt._
import sbt.Keys._
import complete.DefaultParsers._

object JibPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val Jib: Configuration      = config("jib")
    val JibExtra: Configuration = config("jib-extra-files")

    object JibPlatforms {
      val arm64 = new Platform("arm64", "linux")
      val amd64 = new Platform("amd64", "linux")
    }

    sealed trait JibImageFormat
    object JibImageFormat {
      case object Docker extends JibImageFormat
      case object OCI    extends JibImageFormat
    }

    val jibBaseImage = settingKey[String]("jib base image")
    val jibBaseImageCredentialHelper =
      settingKey[Option[String]]("jib base image credential helper cli name (e.g. ecr-login)")
    val jibJvmFlags        = settingKey[List[String]]("jib default jvm flags")
    val jibArgs            = settingKey[List[String]]("jib default args")
    val jibEntrypoint      = settingKey[Option[List[String]]]("jib entrypoint")
    val jibImageFormat     = settingKey[JibImageFormat]("jib default image format")
    val jibDockerBuild     = taskKey[ImageReference]("jib build docker image")
    val jibImageBuild      = taskKey[ImageReference]("jib build image (does not need docker)")
    val jibTarImageBuild   = inputKey[Unit]("jib build tar image")
    val jibJavaDockerBuild = taskKey[ImageReference]("jib build docker image, uses JavaContainerBuilder from jib-core")
    val jibJavaImageBuild =
      taskKey[ImageReference]("jib build image (does not need docker), uses JavaContainerBuilder from jib-core")
    val jibJavaTarImageBuild           = inputKey[Unit]("jib build tar image, uses JavaContainerBuilder from jib-core")
    val jibTargetImageCredentialHelper = settingKey[Option[String]]("jib target image credential helper cli name")
    val jibRegistry                    = settingKey[String]("jib target image registry (defaults to docker hub)")
    val jibOrganization                = settingKey[String]("jib docker organization (defaults to organization)")
    val jibName                        = settingKey[String]("jib image name (defaults to project name)")
    val jibVersion                     = settingKey[String]("jib version (defaults to version)")
    val jibEnvironment                 = settingKey[Map[String, String]]("jib docker env variables")
    val jibPlatforms                   = settingKey[Set[Platform]]("jib platforms to build for")
    val jibLabels                      = settingKey[Map[String, String]]("jib docker labels")
    val jibTags                        = settingKey[List[String]]("jib image tags (in addition to jibVersion)")
    val jibTarget                      = settingKey[File]("""jib target folder (defaults to target.value / "jib")""")
    val jibAllowInsecureRegistries     = settingKey[Boolean]("""allow pushing to insecure registries""")
    val jibSendCredentialsOverHttp     = settingKey[Boolean]("""allow sending credentials over unencrypted HTTP""")
    val jibUser =
      settingKey[Option[String]]("jib user and group to run the container as")
    val jibMappings = taskKey[Seq[(File, String)]](
      "jib additional resource mappings, formatted as <source file resource> -> <full path on container>"
    )
    val jibExtraMappings =
      taskKey[Seq[(File, String)]]("jib extra file mappings / i.e. java agents (see above for formatting)")
    val jibUseCurrentTimestamp =
      settingKey[Boolean]("jib use current timestamp for image creation time. Default to Epoch")
    val jibCustomRepositoryPath = settingKey[Option[String]]("jib custom repository path freeform path structure")

    private[jib] object Private {
      val sbtLayerConfiguration = taskKey[SbtLayerConfigurations]("jib layer configuration")
      val sbtConfiguration      = taskKey[SbtConfiguration]("jib sbt configuration")
    }
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    // public values
    jibBaseImage                   := "registry.hub.docker.com/schmitch/graalvm:latest",
    jibBaseImageCredentialHelper   := None,
    jibTargetImageCredentialHelper := None,
    jibUser                        := None,
    jibJvmFlags                    := Nil,
    jibArgs                        := Nil,
    jibEntrypoint                  := None,
    jibImageFormat                 := JibImageFormat.Docker,
    jibRegistry                    := "registry.hub.docker.com",
    jibOrganization                := organization.value,
    jibName                        := name.value,
    jibVersion                     := version.value,
    jibEnvironment                 := Map.empty,
    jibPlatforms                   := Set(JibPlatforms.amd64),
    jibLabels                      := Map.empty,
    jibTags                        := List.empty,
    Jib / mappings                 := Nil,
    JibExtra / mappings            := Nil,
    jibMappings                    := (Jib / mappings).value,
    jibExtraMappings               := (JibExtra / mappings).value,
    jibUseCurrentTimestamp         := false,
    jibCustomRepositoryPath        := None,
    jibTarget                      := target.value / "jib",
    jibAllowInsecureRegistries     := false,
    jibSendCredentialsOverHttp     := false,
    // private values
    Private.sbtLayerConfiguration := {
      val stageDirectory     = target.value / "stage"
      val stageDirectoryPath = stageDirectory.toPath
      if (Files.notExists(stageDirectoryPath)) {
        Files.createDirectories(stageDirectoryPath)
      }
      val staged = Stager.stage(Jib.name)(streams.value, stageDirectory, jibMappings.value)

      SbtLayerConfigurations(
        target.value,
        (Compile / products).value,
        (Compile / resourceDirectories).value,
        (Compile / resources).value,
        (Compile / internalDependencyAsJars).value,
        (externalDependencyClasspath or (Runtime / externalDependencyClasspath)).value,
        jibExtraMappings.value,
        staged,
        jibMappings.value
      )
    },
    Private.sbtConfiguration := {
      val baseImage = ImageReference.parse(jibBaseImage.value)

      new SbtConfiguration(
        sLog.value,
        Private.sbtLayerConfiguration.value,
        (Compile / packageBin / mainClass).value,
        (Compile / packageBin / discoveredMainClasses).value,
        jibTarget.value / "internal",
        credentials.value,
        baseImage,
        jibRegistry.value,
        jibOrganization.value,
        jibName.value,
        jibVersion.value,
        jibCustomRepositoryPath.value,
        jibAllowInsecureRegistries.value,
        jibSendCredentialsOverHttp.value,
        jibTarget.value
      )

    },
    jibDockerBuild := SbtDockerBuild.task(
      target.value,
      streams.value.log,
      Private.sbtConfiguration.value,
      jibBaseImageCredentialHelper.value,
      jibJvmFlags.value,
      jibArgs.value,
      jibEntrypoint.value,
      jibEnvironment.value,
      jibLabels.value,
      jibTags.value,
      jibUser.value,
      jibUseCurrentTimestamp.value,
      jibPlatforms.value
    ),
    jibImageBuild := SbtImageBuild.task(
      target.value,
      streams.value.log,
      Private.sbtConfiguration.value,
      jibBaseImageCredentialHelper.value,
      jibTargetImageCredentialHelper.value,
      jibJvmFlags.value,
      jibArgs.value,
      jibEntrypoint.value,
      jibImageFormat.value,
      jibEnvironment.value,
      jibLabels.value,
      jibTags.value,
      jibUser.value,
      jibUseCurrentTimestamp.value,
      jibPlatforms.value
    ),
    jibTarImageBuild := {
      spaceDelimited("<path>").parsed.headOption match {
        case Some(pathString) =>
          SbtTarImageBuild.task(
            target.value,
            new File(pathString),
            streams.value.log,
            Private.sbtConfiguration.value,
            jibBaseImageCredentialHelper.value,
            jibJvmFlags.value,
            jibArgs.value,
            jibEntrypoint.value,
            jibImageFormat.value,
            jibEnvironment.value,
            jibLabels.value,
            jibTags.value,
            jibUser.value,
            jibUseCurrentTimestamp.value
          )
        case None =>
          streams.value.log.error("could not create jib tar image, cause path is not set")
      }
    },
    jibJavaDockerBuild := SbtJavaDockerBuild.task(
      target.value,
      streams.value.log,
      Private.sbtConfiguration.value,
      jibBaseImageCredentialHelper.value,
      jibJvmFlags.value,
      jibArgs.value,
      jibEnvironment.value,
      jibLabels.value,
      jibTags.value,
      jibUser.value,
      jibUseCurrentTimestamp.value,
      jibPlatforms.value
    ),
    jibJavaImageBuild := SbtJavaImageBuild.task(
      target.value,
      streams.value.log,
      Private.sbtConfiguration.value,
      jibBaseImageCredentialHelper.value,
      jibTargetImageCredentialHelper.value,
      jibJvmFlags.value,
      jibArgs.value,
      jibImageFormat.value,
      jibEnvironment.value,
      jibLabels.value,
      jibTags.value,
      jibUser.value,
      jibUseCurrentTimestamp.value,
      jibPlatforms.value
    ),
    jibJavaTarImageBuild := {
      spaceDelimited("<path>").parsed.headOption match {
        case Some(pathString) =>
          SbtJavaTarImageBuild.task(
            target.value,
            new File(pathString),
            streams.value.log,
            Private.sbtConfiguration.value,
            jibBaseImageCredentialHelper.value,
            jibJvmFlags.value,
            jibArgs.value,
            jibImageFormat.value,
            jibEnvironment.value,
            jibLabels.value,
            jibTags.value,
            jibUser.value,
            jibUseCurrentTimestamp.value
          )
        case None =>
          streams.value.log.error("could not create jib java tar image, cause path is not set")
      }
    },
    jibDockerBuild     := jibDockerBuild.dependsOn(Compile / compile).value,
    jibImageBuild      := jibImageBuild.dependsOn(Compile / compile).value,
    jibJavaDockerBuild := jibJavaDockerBuild.dependsOn(Compile / compile).value,
    jibJavaImageBuild  := jibJavaImageBuild.dependsOn(Compile / compile).value
  )

}
