package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan.{AbsoluteUnixPath, FileEntriesLayer}
import com.google.cloud.tools.jib.api.{Containerizer, Credential, CredentialRetriever, ImageReference, LogEvent, RegistryImage}
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory
import com.google.cloud.tools.jib.global.JibSystemProperties
import com.google.common.collect.ImmutableList
import sbt.librarymanagement.ivy.Credentials
import sbt.util.Logger

import java.io.File
import java.nio.file.{Files, Path}
import java.util.Optional
import scala.collection.JavaConverters._

private[jib] class SbtConfiguration(
    logger: Logger,
    layerConfigurations: List[FileEntriesLayer],
    mainClass: Option[String],
    discoveredMainClasses: Seq[String],
    targetValue: File,
    credentials: Seq[Credentials],
    val baseImageReference: ImageReference,
    val registry: String,
    val organization: String,
    val name: String,
    val version: String,
    customRepositoryPath: Option[String],
    val allowInsecureRegistries: Boolean,
    sendCredentialsOverHttp: Boolean,
    val target: File,
) {

  private val USER_AGENT_SUFFIX = "jib-sbt-plugin"

  // See: https://github.com/GoogleContainerTools/jib/blob/v0.19.0-core/jib-cli/src/main/java/com/google/cloud/tools/jib/cli/Containerizers.java#L98-L102
  System.setProperty(JibSystemProperties.SEND_CREDENTIALS_OVER_HTTP, sendCredentialsOverHttp.toString)

  val repository: String = customRepositoryPath.getOrElse(organization + "/" + name)

  private val PLUGIN_NAME     = "jib-sbt-plugin"
  private val JAR_PLUGIN_NAME = "'sbt-jar-plugin'"

  def getPluginName: String = PLUGIN_NAME

  def getLayerConfigurations: ImmutableList[FileEntriesLayer] = {
    ImmutableList.copyOf[FileEntriesLayer](layerConfigurations.asJavaCollection)
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
                           credentialsEnv: (String, String),
                           credHelper: Option[String]) = {

    val image = RegistryImage.named(imageReference)

    val factory = CredentialRetrieverFactory.forImage(imageReference, { case (logEvent: LogEvent) => { /* no-op */ } })

    val (usernameEnv, passwordEnv) = credentialsEnv

    image.addCredentialRetriever(retrieveEnvCredentials(usernameEnv, passwordEnv))
    image.addCredentialRetriever(retrieveSbtCredentials(imageReference))
    image.addCredentialRetriever(factory.dockerConfig())
    image.addCredentialRetriever(factory.wellKnownCredentialHelpers())
    image.addCredentialRetriever(factory.googleApplicationDefaultCredentials())

    credHelper.foreach { helper =>
      image.addCredentialRetriever(factory.dockerCredentialHelper(helper))
    }

    image
  }

  private def retrieveEnvCredentials(usernameEnv: String, passwordEnv: String): CredentialRetriever = {
    () => {
      val option = for {
        username <- sys.env.get(usernameEnv)
        password <- sys.env.get(passwordEnv)
      } yield Credential.from(username, password)

      Optional.ofNullable(option.orNull)
    }
  }

  private def retrieveSbtCredentials(imageReference: ImageReference): CredentialRetriever = {
    () => {
      val option = Credentials.forHost(credentials, imageReference.getRegistry).map(c => Credential.from(c.userName, c.passwd))
      Optional.ofNullable(option.orNull)
    }
  }

  def baseImageFactory(jibBaseImageCredentialHelper: Option[String]): RegistryImage = {
    imageFactory(baseImageReference, ("JIB_BASE_IMAGE_USERNAME", "JIB_BASE_IMAGE_PASSWORD"), jibBaseImageCredentialHelper)
  }

  def targetImageFactory(jibTargetImageCredentialHelper: Option[String]): RegistryImage = {
    imageFactory(targetImageReference, ("JIB_TARGET_IMAGE_USERNAME", "JIB_TARGET_IMAGE_PASSWORD"), jibTargetImageCredentialHelper)
  }

  def configureContainerizer(containerizer: Containerizer): Containerizer = containerizer
    .setAllowInsecureRegistries(allowInsecureRegistries)
    .setToolName(USER_AGENT_SUFFIX)
    .setApplicationLayersCache(target.toPath.resolve("application-layer-cache"))
    .setBaseImageLayersCache(target.toPath.resolve("base-image-layer-cache"))
}
