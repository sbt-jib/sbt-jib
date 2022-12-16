package de.gccc.jib

import com.google.cloud.tools.jib.api.buildplan._
import com.google.cloud.tools.jib.api._
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory
import com.google.cloud.tools.jib.global.JibSystemProperties

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Path }
import scala.jdk.CollectionConverters._
import scala.compat.java8.FunctionConverters._
import scala.compat.java8.OptionConverters._
import scala.language.postfixOps

private[jib] object JibCommon {

  private def isSnapshotDependency(path: Path) = path.toString.endsWith("-SNAPSHOT.jar")

  private def addToClasspath(
      builder: JavaContainerBuilder,
      mappings: Seq[(File, String)],
      loggerWarn: String => Unit
  ): Unit =
    builder.addToClasspath(
      mappings.map { case (file, ignored) =>
        if (file.toString != ignored) {
          loggerWarn(s"The file `$file` won't be mapped to `$ignored` in the container, but directly to `$file`.")
        }
        file.toPath
      }.asJava
    )

  def prepareJavaContainerBuilder(builder: JavaContainerBuilder)(
      layerConfigurations: SbtLayerConfigurations,
      mainClass: Option[String],
      jvmFlags: List[String],
      loggerWarn: String => Unit
  ): JavaContainerBuilder = {
    builder.addDependencies(
      layerConfigurations.external.map(_.data.toPath).filterNot(isSnapshotDependency).asJava
    )
    builder.addSnapshotDependencies(
      layerConfigurations.external.map(_.data.toPath).filter(isSnapshotDependency).asJava
    )
    addToClasspath(builder, layerConfigurations.mappings, loggerWarn)
    addToClasspath(builder, layerConfigurations.extraMappings, loggerWarn)
    builder.addProjectDependencies(
      layerConfigurations.internalDependencies.map(_.data.toPath).asJava
    )
    layerConfigurations.resourceDirectories.filter(_.exists).foreach { f =>
      builder.addResources(f.toPath)
    }
    layerConfigurations.classes.filter(_.exists).foreach { f =>
      builder.addClasses(f.toPath, (p: Path) => p.toString.endsWith(".class"))
    }
    builder.setMainClass(mainClass.orNull).addJvmFlags(jvmFlags.asJava)
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
    .setCreationTime(TimestampHelper.useCurrentTimestamp(useCurrentTimestamp))

  private def imageFactory(
      imageReference: ImageReference,
      credentialsEnv: (String, String),
      credHelper: Option[String],
      credsForHost: String => Option[(String, String)],
      logger: LogEvent => Unit
  ): RegistryImage = {
    val image                      = RegistryImage.named(imageReference)
    val factory                    = CredentialRetrieverFactory.forImage(imageReference, logger.asJava)
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

  private def retrieveEnvCredentials(usernameEnv: String, passwordEnv: String): CredentialRetriever = { () =>
    {
      val option = for {
        username <- sys.env.get(usernameEnv)
        password <- sys.env.get(passwordEnv)
      } yield Credential.from(username, password)
      option.asJava
    }
  }

  private def retrieveSbtCredentials(
      imageReference: ImageReference,
      credsForHost: String => Option[(String, String)]
  ): CredentialRetriever = { () =>
    {
      val option = credsForHost(imageReference.getRegistry).map(Credential.from _ tupled)
      option.asJava
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
      target: File
  ): Containerizer = {
    additionalTags.foldRight(containerizer)((tag, image) => image.withAdditionalTag(tag))
    containerizer
      .setAllowInsecureRegistries(allowInsecureRegistries)
      .setToolName(USER_AGENT_SUFFIX)
      .setApplicationLayersCache(target.toPath.resolve("application-layer-cache"))
      .setBaseImageLayersCache(target.toPath.resolve("base-image-layer-cache"))
  }

  def writeJibOutputFiles(container: JibContainer)(targetDirectory: File): Unit = {
    Files.write(targetDirectory.toPath.resolve("jib-image.digest"), container.getDigest.toString.getBytes(UTF_8))
    Files.write(targetDirectory.toPath.resolve("jib-image.id"), container.getImageId.toString.getBytes(UTF_8))
    val jsonString =
      s"""{
         |   "image": "${container.getTargetImage}",
         |   "imageId": "${container.getImageId}",
         |   "imageDigest": "${container.getDigest}",
         |   "tags": ${container.getTags.asScala.mkString("[\"", "\", \"", "\"]")}
         |}""".stripMargin
    Files.write(targetDirectory.toPath.resolve("jib-image.json"), jsonString.getBytes(UTF_8))
  }

  // See: https://github.com/GoogleContainerTools/jib/blob/v0.19.0-core/jib-cli/src/main/java/com/google/cloud/tools/jib/cli/Containerizers.java#L98-L102
  def setSendCredentialsOverHttp(sendCredentialsOverHttp: Boolean): Unit =
    System.setProperty(JibSystemProperties.SEND_CREDENTIALS_OVER_HTTP, sendCredentialsOverHttp.toString)
}
