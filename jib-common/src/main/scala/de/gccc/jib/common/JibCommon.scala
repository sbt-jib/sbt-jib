package de.gccc.jib.common

import com.google.cloud.tools.jib.api.buildplan._
import com.google.cloud.tools.jib.api._
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory
import com.google.cloud.tools.jib.global.JibSystemProperties

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Path }
import java.time.Instant
import java.util.Optional
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOption
import scala.language.postfixOps

object JibCommon {

  private def isSnapshotDependency(path: Path) = path.toString.endsWith("-SNAPSHOT.jar")

  def prepareJavaContainerBuilder(builder: JavaContainerBuilder)(
      externalDependencies: List[Path],
      otherFilesToClasspath: List[Path],
      internalDependencies: List[Path],
      resourceFilesDirectories: List[Path],
      classFilesDirectories: List[Path],
      mainClass: Option[String],
      jvmFlags: List[String]
  ): Unit = {
    builder.addDependencies(externalDependencies.filterNot(isSnapshotDependency).asJava)
    builder.addSnapshotDependencies(externalDependencies.filter(isSnapshotDependency).asJava)
    builder.addToClasspath(otherFilesToClasspath.asJava)
    builder.addProjectDependencies(internalDependencies.asJava)
    resourceFilesDirectories.filter(_.toFile.exists).foreach(builder.addResources)
    classFilesDirectories
      .filter(_.toFile.exists)
      .foreach(builder.addClasses(_, (p: Path) => p.toString.endsWith(".class")))
    builder.setMainClass(mainClass.orNull)
    builder.addJvmFlags(jvmFlags.asJava)
  }

  def prepareJibContainerBuilder(builder: JibContainerBuilder)(
      ports: Set[Port],
      args: List[String],
      internalImageFormat: ImageFormat,
      environment: Map[String, String],
      labels: Map[String, String],
      user: Option[String],
      useCurrentTimestamp: Boolean,
      platforms: Set[Platform]
  ): JibContainerBuilder = builder
    .setEnvironment(environment.asJava)
    .setPlatforms(platforms.asJava)
    .setLabels(labels.asJava)
    .setUser(user.orNull)
    .setProgramArguments(args.asJava)
    .setFormat(internalImageFormat)
    .setExposedPorts(ports.asJava)
    .setCreationTime(this.useCurrentTimestamp(useCurrentTimestamp))

  private def imageFactory(
      imageReference: ImageReference,
      credentialsEnv: (String, String),
      credHelper: Option[String],
      credsForHost: String => Option[(String, String)],
      logger: LogEvent => Unit
  ): RegistryImage = {
    val image                      = RegistryImage.named(imageReference)
    val factory                    = CredentialRetrieverFactory.forImage(imageReference, e => logger(e))
    val (usernameEnv, passwordEnv) = credentialsEnv

    image.addCredentialRetriever(retrieveEnvCredentials(usernameEnv, passwordEnv))
    image.addCredentialRetriever(retrieveSbtCredentials(imageReference, credsForHost))
    image.addCredentialRetriever(factory.dockerConfig())
    image.addCredentialRetriever(factory.wellKnownCredentialHelpers())
    image.addCredentialRetriever(factory.googleApplicationDefaultCredentials())

    credHelper.foreach { helper =>
      image.addCredentialRetriever(factory.dockerCredentialHelper(helper))
    }

    image
  }

  private def retrieveEnvCredentials(usernameEnv: String, passwordEnv: String): CredentialRetriever =
    new CredentialRetriever {
      def retrieve(): Optional[Credential] = {
        val option = for {
          username <- sys.env.get(usernameEnv)
          password <- sys.env.get(passwordEnv)
        } yield Credential.from(username, password)
        option.toJava
      }
    }

  private def retrieveSbtCredentials(
      imageReference: ImageReference,
      credsForHost: String => Option[(String, String)]
  ): CredentialRetriever = new CredentialRetriever {
    def retrieve(): Optional[Credential] = {
      val option = credsForHost(imageReference.getRegistry).map(Credential.from _ tupled)
      option.toJava
    }
  }

  def baseImageFactory(baseImageReference: ImageReference)(
      jibBaseImageCredentialHelper: Option[String],
      credsForHost: String => Option[(String, String)],
      logger: LogEvent => Unit
  ): RegistryImage = {
    imageFactory(
      baseImageReference,
      ("JIB_BASE_IMAGE_USERNAME", "JIB_BASE_IMAGE_PASSWORD"),
      jibBaseImageCredentialHelper,
      credsForHost,
      logger
    )
  }

  def targetImageFactory(targetImageReference: ImageReference)(
      jibTargetImageCredentialHelper: Option[String],
      credsForHost: String => Option[(String, String)],
      logger: LogEvent => Unit
  ): RegistryImage = {
    imageFactory(
      targetImageReference,
      ("JIB_TARGET_IMAGE_USERNAME", "JIB_TARGET_IMAGE_PASSWORD"),
      jibTargetImageCredentialHelper,
      credsForHost,
      logger
    )
  }

  def configureContainerizer(containerizer: Containerizer)(
      additionalTags: List[String],
      allowInsecureRegistries: Boolean,
      USER_AGENT_SUFFIX: String,
      target: Path
  ): Unit = {
    additionalTags.foldRight(containerizer)((tag, image) => image.withAdditionalTag(tag))
    containerizer
      .setAllowInsecureRegistries(allowInsecureRegistries)
      .setToolName(USER_AGENT_SUFFIX)
      .setApplicationLayersCache(target.resolve("application-layer-cache"))
      .setBaseImageLayersCache(target.resolve("base-image-layer-cache"))
  }

  def writeJibOutputFiles(container: JibContainer)(targetDirectory: Path): Unit = {
    Files.write(targetDirectory.resolve("jib-image.digest"), container.getDigest.toString.getBytes(UTF_8))
    Files.write(targetDirectory.resolve("jib-image.id"), container.getImageId.toString.getBytes(UTF_8))
    val jsonString =
      s"""{
         |   "image": "${container.getTargetImage}",
         |   "imageId": "${container.getImageId}",
         |   "imageDigest": "${container.getDigest}",
         |   "tags": ${container.getTags.asScala.mkString("[\"", "\", \"", "\"]")}
         |}""".stripMargin
    Files.write(targetDirectory.resolve("jib-image.json"), jsonString.getBytes(UTF_8))
  }

  // See: https://github.com/GoogleContainerTools/jib/blob/v0.19.0-core/jib-cli/src/main/java/com/google/cloud/tools/jib/cli/Containerizers.java#L98-L102
  def setSendCredentialsOverHttp(sendCredentialsOverHttp: Boolean): Unit =
    System.setProperty(JibSystemProperties.SEND_CREDENTIALS_OVER_HTTP, sendCredentialsOverHttp.toString)

  def useCurrentTimestamp(useCurrentTimestamp: Boolean): Instant =
    if (useCurrentTimestamp) Instant.now() else Instant.EPOCH
}
