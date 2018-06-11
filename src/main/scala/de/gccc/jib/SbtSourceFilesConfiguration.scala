package de.gccc.jib

import java.nio.file.Path

import com.google.cloud.tools.jib.builder.SourceFilesConfiguration
import com.google.common.collect.ImmutableList

import scala.collection.JavaConverters._

class SbtSourceFilesConfiguration(
    baseArtifact: Path,
    dependencyClasspath: List[Path],
    externalDependencyClasspath: List[Path]
) extends SourceFilesConfiguration {
  private val DEPENDENCIES_PATH_ON_IMAGE = "/app/libs/"
  private val RESOURCES_PATH_ON_IMAGE = "/app/resources/"
  private val CLASSES_PATH_ON_IMAGE = "/app/classes/"

  /**
    * @return the source files for the dependencies layer. These files should be in a deterministic
    *     order.
    */
  override def getDependenciesFiles: ImmutableList[Path] = {
    ImmutableList.sortedCopyOf(externalDependencyClasspath.asJava)
  }

  /**
    * @return the source files for the resources layer. These files should be in a deterministic
    *     order.
    */
  override def getResourcesFiles: ImmutableList[Path] = {
    // FIXME: this should not be empty
    ImmutableList.builder().build()
  }

  /**
    * @return the source files for the classes layer. These files should be in a deterministic order.
    */
  override def getClassesFiles: ImmutableList[Path] = {
    ImmutableList.sortedCopyOf((baseArtifact :: dependencyClasspath).asJava)
  }

  /**
    * @return the Unix-style path where the dependencies source files are placed in the container
    *     filesystem. Must end with backslash.
    */
  override def getDependenciesPathOnImage: String = DEPENDENCIES_PATH_ON_IMAGE

  /**
    * @return the Unix-style path where the resources source files are placed in the container
    *     filesystem. Must end with backslash.
    */
  override def getResourcesPathOnImage: String = RESOURCES_PATH_ON_IMAGE

  /**
    * @return the Unix-style path where the classes source files are placed in the container
    *     filesystem. Must end with backslash.
    */
  override def getClassesPathOnImage: String = CLASSES_PATH_ON_IMAGE
}
