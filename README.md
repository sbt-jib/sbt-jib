# sbt-jib

This project tries to make a sbt plugin for the awesome [jib](https://github.com/GoogleContainerTools/jib) project from google.

## settings
    
| name | type | description |
| ---                            | --- | --- |
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

