package de.gccc.jib

import java.io.File
import java.nio.file.{ Files, Path }

import com.google.cloud.tools.jib.api.{
  AbsoluteUnixPath,
  Credential,
  ImageReference,
  LayerConfiguration,
  LogEvent,
  RegistryImage
}
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory
import com.google.common.collect.ImmutableList
import sbt.librarymanagement.ivy.{ Credentials, DirectCredentials }
import sbt.util.Logger

import scala.collection.JavaConverters._

private[jib] class SbtConfiguration(
    logger: Logger,
    layerConfigurations: List[LayerConfiguration],
    mainClass: Option[String],
    discoveredMainClasses: Seq[String],
    targetValue: File,
    credentials: Seq[Credentials],
    val baseImageReference: ImageReference,
    val registry: String,
    val organization: String,
    val name: String,
    val version: String,
    customRepositoryPath: Option[String]
) {

  val repository: String = customRepositoryPath.getOrElse(organization + "/" + name)

  private def generateCredentials(sbtCreds: Option[DirectCredentials], usernameEnv: String, passwdEnv: String) = {
    sbtCreds.orElse {
      val usernameOption = sys.env.get(usernameEnv)
      val passwordOption = sys.env.get(passwdEnv)
      for {
        username <- usernameOption
        password <- passwordOption
      } yield new DirectCredentials("", "", username, password)
    }.map { sbtCredentials =>
      Credential.from(sbtCredentials.userName, sbtCredentials.passwd)
    }
  }

  private val PLUGIN_NAME     = "jib-sbt-plugin"
  private val JAR_PLUGIN_NAME = "'sbt-jar-plugin'"

  def getPluginName: String = PLUGIN_NAME

  def getLayerConfigurations: ImmutableList[LayerConfiguration] = {
    ImmutableList.copyOf[LayerConfiguration](layerConfigurations.asJavaCollection)
  }

  def getCacheDirectory: Path = {
    val targetPath = targetValue.toPath
    if (Files.notExists(targetPath)) {
      Files.createDirectories(targetPath)
    }
    targetPath
  }

  def getJarPluginName: String = JAR_PLUGIN_NAME

  lazy val targetImageReference: ImageReference =
    ImageReference.of(registry, repository, version)

  lazy val baseImageCredentials: Option[Credential] = {
    generateCredentials(
      Credentials.forHost(credentials, baseImageReference.getRegistry),
      "JIB_BASE_IMAGE_USERNAME",
      "JIB_BASE_IMAGE_PASSWORD"
    )
  }

  lazy val targetImageCredentials: Option[Credential] = {
    generateCredentials(
      Credentials.forHost(credentials, targetImageReference.getRegistry),
      "JIB_TARGET_IMAGE_USERNAME",
      "JIB_TARGET_IMAGE_PASSWORD"
    )
  }

  def entrypoint(jvmFlags: List[String], entrypoint: Option[List[String]]): java.util.List[String] = {
    entrypoint match {
      case Some(list) => list.asJava
      case None =>
        val appRoot = AbsoluteUnixPath.get("/app")
        val pickedMainClass = mainClass.getOrElse {
          discoveredMainClasses.toList match {
            case one :: Nil => one
            case first :: _ =>
              logger.warn(
                s"using first discovered main class for entrypoint ($first) this may not be what you want. Use the mainClass setting to specify the one you want."
              )
              first
            case Nil =>
              sys.error("no main class found for container image entrypoint")
          }
        }
        JavaEntrypointConstructor.makeDefaultEntrypoint(appRoot, jvmFlags.asJava, pickedMainClass)
    }
  }

  private def imageFactory(imageReference: ImageReference,
                           credentials: Option[Credential],
                           credHelper: Option[String]) = {

    val image = RegistryImage.named(imageReference)

    val factory = CredentialRetrieverFactory.forImage(imageReference, { case (logEvent: LogEvent) => { /* no-op */ } })

    image.addCredentialRetriever(factory.wellKnownCredentialHelpers())

    credHelper.foreach { helper =>
      image.addCredentialRetriever(factory.dockerCredentialHelper(helper))
    }

    credentials.foreach { credential =>
      image.addCredential(credential.getUsername, credential.getPassword)
    }

    image
  }

  def baseImageFactory(jibBaseImageCredentialHelper: Option[String]): RegistryImage = {
    imageFactory(baseImageReference, baseImageCredentials, jibBaseImageCredentialHelper)
  }

  def targetImageFactory(jibTargetImageCredentialHelper: Option[String]): RegistryImage = {
    imageFactory(targetImageReference, targetImageCredentials, jibTargetImageCredentialHelper)
  }

}
