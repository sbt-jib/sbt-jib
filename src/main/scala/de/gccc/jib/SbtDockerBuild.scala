package de.gccc.jib
import sbt.internal.util.ManagedLogger

private[jib] object SbtDockerBuild {

  // private val USER_AGENT_SUFFIX = "jib-sbt-plugin"

  def task(
      logger: ManagedLogger,
      configuration: SbtConfiguration,
      jibBaseImageCredentialHelper: Option[String],
      jibTargetImageCredentialHelper: Option[String],
      defaultImage: String,
      jvmFlags: List[String],
      args: List[String],
      environment: Map[String, String]
  ): Unit = {
    throw new Exception("Building to Docker Clients is currently unsupported")
    /*
    if (!new DockerClient().isDockerInstalled) {
      throw new Exception("Build to Docker daemon failed")
    }

    try {
      val targetImage = DockerDaemonImage.named(configuration.targetImageReference)


      val jib = Jib.from(configuration.baseImageFactory(jibTargetImageCredentialHelper))

      configuration.getLayerConfigurations.forEach { configuration =>
        jib.addLayer(configuration)
      }

      jib
        .setEnvironment(environment.asJava)
        .setProgramArguments(args.asJava)
        .setFormat(ImageFormat.Docker)
        .setEntrypoint(configuration.entrypoint(jvmFlags))
        .containerize(
          Containerizer
            .to(configuration.targetImageFactory(jibTargetImageCredentialHelper))
            .setToolName(USER_AGENT_SUFFIX)

          // .setBaseImageLayersCache()
          // .setApplicationLayersCache()
        )

      logger.info("image successfully created & uploaded")
    } catch {
      case e @ (_: CacheDirectoryCreationException) =>
        throw new Exception(e.getMessage, e.getCause)
    }
    */
  }

}
