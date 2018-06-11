package de.gccc.jib

import com.google.cloud.tools.jib.image.ImageReference
import sbt._
import sbt.Keys._

object JibPlugin extends AutoPlugin {

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
    val jibImageBuild  = taskKey[Unit]("jib build image (does not need docker)")
    val jibRegistry = settingKey[String]("jib target image registry (defaults to docker hub)")
    val jibOrganization = settingKey[String]("jib docker organization (defaults to organization)")
    val jibName = settingKey[String]("jib image name (defaults to project name)")
    val jibVersion = settingKey[String]("jib version (defaults to version)")

    private[jib] object Private {
      val sbtSourceFilesConfiguration = {
        taskKey[SbtSourceFilesConfiguration]("jib source file settings")
      }
      val sbtConfiguration = taskKey[SbtConfiguration]("jib sbt configuration")
    }
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    // public values
    jibBaseImage := "registry.hub.docker.com/schmitch/graalvm:latest",
    jibJvmFlags := Nil,
    jibArgs := Nil,
    jibImageFormat := JibImageFormat.Docker,
    jibRegistry := "registry.hub.docker.com",
    jibOrganization := organization.value,
    jibName := name.value,
    jibVersion := version.value,
    // private values
    Private.sbtSourceFilesConfiguration := {
      val artifact   = (artifactPath in (Compile, packageBin)).value.toPath
      val external   = (externalDependencyClasspath or (externalDependencyClasspath in Runtime)).value
      val dependency = (internalDependencyAsJars in Compile).value
      new SbtSourceFilesConfiguration(
        artifact,
        dependency.map(_.data.toPath).toList,
        external.map(_.data.toPath).toList
      )
    },
    Private.sbtConfiguration := {
      val baseImage = ImageReference.parse(jibBaseImage.value)

      new SbtConfiguration(
        sLog.value,
        Private.sbtSourceFilesConfiguration.value,
        (mainClass in (Compile, packageBin)).value,
        target.value / "jib",
        credentials.value,
        baseImage,
        jibRegistry.value,
        jibOrganization.value,
        jibName.value,
        jibVersion.value
      )
    },
    jibDockerBuild := SbtDockerBuild.task(
      Private.sbtConfiguration.value,
      jibBaseImage.value,
      jibJvmFlags.value,
      jibArgs.value
    ),
    jibImageBuild := SbtImageBuild.task(
      Private.sbtConfiguration.value,
      jibJvmFlags.value,
      jibArgs.value,
      jibImageFormat.value
    ),
    jibDockerBuild := jibDockerBuild.dependsOn(packageBin in Compile).value,
    jibImageBuild := jibImageBuild.dependsOn(packageBin in Compile).value,
  )

}
