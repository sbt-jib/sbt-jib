# sbt-jib

This project tries to make a sbt plugin for the awesome [jib](https://github.com/GoogleContainerTools/jib) project from google.

## settings
    
| name | type | description |
| ---                                | --- | --- |
| **jibTarget**                      | Option[File] | jib work directory |
| **jibBaseImage**                   | String | jib base image |
| **jibBaseImageCredentialHelper**   | Option[String]] | jib base image credential helper cli name (e.g. ecr-login) |
| **jibJvmFlags**                    | List[String]] | jib default jvm flags |
| **jibArgs**                        | List[String]] | jib default args |
| **jibEntrypoint**                  | Option[List[String]] | jib entrypoint |
| **jibImageFormat**                 | JibImageFormat | jib default image format |
| **jibTargetImageCredentialHelper** | Option[String] | jib target image credential helper cli name |
| **jibRegistry**                    | String | jib target image registry (defaults to docker hub) |
| **jibOrganization**                | String | jib docker organization (defaults to organization) |
| **jibName**                        | String | jib image name (defaults to project name) |
| **jibVersion**                     | String | jib version (defaults to version) |
| **jibEnvironment**                 | Map[String, String] | jib docker env variables |
| **jibLabels**                      | Map[String, String] | jib docker labels |
| **jibUser**                        | Option[String] | jib user and group to run the container as |
| **jibMappings**                    | Seq[(File, String)] | jib additional resource mappings, <br>formatted as \<source file resource\> -> \<full path on container\> |
| **jibExtraMappings**               | Seq[(File, String)] | jib extra file mappings / i.e. java agents <br>(see above for formatting) |
| **jibUseCurrentTimestamp**         | Boolean | jib use current timestamp for image creation time. Default to Epoch |
| **jibCustomRepositoryPath**        | Option[String] | jib custom repository path freeform path structure. <br>The default repo structure is organization/name |

## commands

| name               | description |
| ---                | --- |
| **jibDockerBuild**     | jib build docker image |
| **jibImageBuild**      | jib build image (does not need docker) |
| **jibTarImageBuild**   | jib build tar image |

## credentials

There are a couple of ways to supply credentials to the image pull and push operations done by jib. The following sources are tested in order:

1. Environment variables: `JIB_BASE_IMAGE_USERNAME` + `JIB_BASE_IMAGE_PASSWORD` for pulling the base image, `JIB_TARGET_IMAGE_USERNAME` + `JIB_TARGET_IMAGE_PASSWORD` for pushing the target image
2. SBT credentials: The plugin looks for a `credentials` entry that matches the host of the image registry
3. The `$HOME/.docker/config.json` for credentials and credential helpers
4. A set of [well known credential helpers](https://github.com/GoogleContainerTools/jib/blob/v0.18.0-core/jib-core/src/main/java/com/google/cloud/tools/jib/frontend/CredentialRetrieverFactory.java#L69)
5. The credential helpers supplied by `jibBaseImageCredentialHelper` or `jibTargetImageCredentialHelper`

## snippets and examples

### injecting java agents

This snippet shows how to inject a Java Agent (Kanela) into a container via jibExtraMappings.

build.sbt
```scala
//...project stuff...
javaAgents += "io.kamon" % "kanela-agent" % "1.0.5" % "dist;runtime;compile"
//...project stuff...
jibBaseImage := "openjdk:11-jre"
jibName := "my-service"
jibRegistry := "some-ecr-repository"
jibUseCurrentTimestamp := true
jibCustomRepositoryPath := Some(jibName.value)
jibJvmFlags := List("-javaagent:/root/lib/kanela-agent.jar")
jibExtraMappings := {
    //javaAgents, Modules and ResolvedAgent come from the sbt-javaagent plugin
    val resolved = javaAgents.value.map { agent =>
        update.value.matching(Modules.exactFilter(agent.module)).headOption map {
            jar => ResolvedAgent(agent, jar)
        }
    }
    for {
        resolvedAgent <- resolved.flatten
    } yield {
        resolvedAgent.artifact -> s"/root/lib/${resolvedAgent.agent.name}.jar"
    }
}
jibTargetImageCredentialHelper := Some("ecr-login") 
jibBaseImageCredentialHelper := Some("ecr-login")
```

