# sbt-jib

This project tries to make a sbt plugin for the awesome [jib](https://github.com/GoogleContainerTools/jib) project from google.

## settings
    
| name | type | description |
| ---                            | --- | --- |
| **jibBaseImage**                   | String | jib base image |
| **jibBaseImageCredentialHelper**   | Option[String]] | jib base image credential helper |
| **jibJvmFlags**                    | List[String]] | jib default jvm flags |
| **jibArgs**                        | List[String]] | jib default args |
| **jibEntrypoint**                  | Option[List[String]] | jib entrypoint |
| **jibImageFormat**                 | JibImageFormat | jib default image format |
| **jibTargetImageCredentialHelper** | Option[String] | jib base image credential helper |
| **jibRegistry**                    | String | jib target image registry (defaults to docker hub) |
| **jibOrganization**                | String | jib docker organization (defaults to organization) |
| **jibName**                        | String | jib image name (defaults to project name) |
| **jibVersion**                     | String | jib version (defaults to version) |
| **jibEnvironment**                 | Map[String, String] | jib docker env variables |
| **jibMappings**                    | Seq[(File, String)] | jib additional resource mappings |
| **jibExtraMappings**               | Seq[(File, String)] | jib extra file mappings / i.e. java agents |
| **jibUseCurrentTimestamp**         | Boolean | jib use current timestamp for image creation time. Default to Epoch |
| **jibCustomRepositoryPath**        | Option[String] | jib custom repository path freeform path structure. <br>The default repo structure is organization/name |

## commands
| name               | description |
| ---                | --- |
| **jibDockerBuild**     | jib build docker image |
| **jibImageBuild**      | jib build image (does not need docker) |
| **jibTarImageBuild**   | jib build tar image |

