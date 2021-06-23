package de.gccc.jib

import java.nio.file.Files
import com.google.cloud.tools.jib.api.ImageReference
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer
import sbt._
import sbt.Keys._
import complete.DefaultParsers._

object JibPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val Jib: Configuration      = config("jib")
    val JibExtra: Configuration = config("jib-extra-files")

    sealed trait JibImageFormat
    object JibImageFormat {
      case object Docker extends JibImageFormat
      case object OCI    extends JibImageFormat
    }

    val jibBaseImage = settingKey[String]("jib base image")
    val jibBaseImageCredentialHelper =
      settingKey[Option[String]]("jib base image credential helper cli name (e.g. ecr-login)")
    val jibJvmFlags                    = settingKey[List[String]]("jib default jvm flags")
    val jibArgs                        = settingKey[List[String]]("jib default args")
    val jibEntrypoint                  = settingKey[Option[List[String]]]("jib entrypoint")
    val jibImageFormat                 = settingKey[JibImageFormat]("jib default image format")
    val jibDockerBuild                 = taskKey[ImageReference]("jib build docker image")
    val jibImageBuild                  = taskKey[ImageReference]("jib build image (does not need docker)")
    val jibTarImageBuild               = inputKey[Unit]("jib build tar image")
    val jibTargetImageCredentialHelper = settingKey[Option[String]]("jib target image credential helper cli name")
    val jibRegistry                    = settingKey[String]("jib target image registry (defaults to docker hub)")
    val jibOrganization                = settingKey[String]("jib docker organization (defaults to organization)")
    val jibName                        = settingKey[String]("jib image name (defaults to project name)")
    val jibVersion                     = settingKey[String]("jib version (defaults to version)")
    val jibEnvironment                 = settingKey[Map[String, String]]("jib docker env variables")
    val jibLabels                      = settingKey[Map[String, String]]("jib docker labels")
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
      val sbtLayerConfiguration = taskKey[List[FileEntriesLayer]]("jib layer configuration")
      val sbtConfiguration      = taskKey[SbtConfiguration]("jib sbt configuration")
    }
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    // public values
    jibBaseImage := "registry.hub.docker.com/schmitch/graalvm:latest",
    jibBaseImageCredentialHelper := None,
    jibTargetImageCredentialHelper := None,
    jibUser := None,
    jibJvmFlags := Nil,
    jibArgs := Nil,
    jibEntrypoint := None,
    jibImageFormat := JibImageFormat.Docker,
    jibRegistry := "registry.hub.docker.com",
    jibOrganization := organization.value,
    jibName := name.value,
    jibVersion := version.value,
    jibEnvironment := Map.empty,
    jibLabels := Map.empty,
    mappings in Jib := Nil,
    mappings in JibExtra := Nil,
    jibMappings := (mappings in Jib).value,
    jibExtraMappings := (mappings in JibExtra).value,
    jibUseCurrentTimestamp := false,
    jibCustomRepositoryPath := None,
    jibTarget := target.value / "jib",
    jibAllowInsecureRegistries := false,
    jibSendCredentialsOverHttp := false,
    // private values
    Private.sbtLayerConfiguration := {
      val stageDirectory     = target.value / "stage"
      val stageDirectoryPath = stageDirectory.toPath
      if (Files.notExists(stageDirectoryPath)) {
        Files.createDirectories(stageDirectoryPath)
      }
      val staged = Stager.stage(Jib.name)(streams.value, stageDirectory, jibMappings.value)

      SbtLayerConfigurations.generate(
        target.value,
        (Compile / products).value,
        (Compile / resourceDirectories).value,
        (Compile / resources).value,
        (Compile / internalDependencyAsJars).value,
        (externalDependencyClasspath or (externalDependencyClasspath in Runtime)).value,
        jibExtraMappings.value,
        staged
      )
    },
    Private.sbtConfiguration := {
      val baseImage = ImageReference.parse(jibBaseImage.value)

      new SbtConfiguration(
        sLog.value,
        Private.sbtLayerConfiguration.value,
        (mainClass in (Compile, packageBin)).value,
        (discoveredMainClasses in (Compile, packageBin)).value,
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
        jibTarget.value,
      )
    },
    jibDockerBuild := SbtDockerBuild.task(
      streams.value.log,
      Private.sbtConfiguration.value,
      jibBaseImageCredentialHelper.value,
      jibJvmFlags.value,
      jibArgs.value,
      jibEntrypoint.value,
      jibEnvironment.value,
      jibLabels.value,
      jibUser.value,
      jibUseCurrentTimestamp.value,
    ),
    jibImageBuild := SbtImageBuild.task(
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
      jibUser.value,
      jibUseCurrentTimestamp.value,
    ),
    jibTarImageBuild := {
      val args = spaceDelimited("<path>").parsed
      args.headOption.foreach { pathString =>
        val file = new File(pathString)
        SbtTarImageBuild.task(
          file,
          streams.value.log,
          Private.sbtConfiguration.value,
          jibBaseImageCredentialHelper.value,
          jibJvmFlags.value,
          jibArgs.value,
          jibEntrypoint.value,
          jibImageFormat.value,
          jibEnvironment.value,
          jibLabels.value,
          jibUser.value,
          jibUseCurrentTimestamp.value,
        )
      }

      if (args.headOption.isEmpty) {
        streams.value.log.error("could not create jib tar image, cause path is not set")
      }
    },
    jibDockerBuild := jibDockerBuild.dependsOn(compile in Compile).value,
    jibImageBuild := jibImageBuild.dependsOn(compile in Compile).value,
  )

}
