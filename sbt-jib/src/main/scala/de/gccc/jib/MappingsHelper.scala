package de.gccc.jib

import sbt.{ IO => _, PathFinder => _, *, given }
import sbt.io.*
import xsbti.FileConverter

import java.io.File

/**
 * A set of helper methods to simplify the writing of mappings.
 *
 * @see
 *   [[https://github.com/sbt/sbt-native-packager/blob/main/src/main/scala/com/typesafe/sbt/packager/MappingsHelper.scala]]
 */
object MappingsHelper extends Mapper {

  /**
   * It lightens the build file if one wants to give a string instead of file.
   *
   * @example
   *   {{{
   * mappings in Universal ++= directory("extra")
   *   }}}
   *
   * @param sourceDir
   * @return
   *   mappings
   */
  def directory(sourceDir: String): Seq[(File, String)] =
    directory(file(sourceDir))

  /**
   * It lightens the build file if one wants to give a string instead of file.
   *
   * @example
   *   {{{
   * mappings in Universal ++= contentOf("extra")
   *   }}}
   *
   * @param sourceDir
   *   as string representation
   * @return
   *   mappings
   */
  def contentOf(sourceDir: String): Seq[(File, String)] =
    contentOf(file(sourceDir))

  def contentOf(baseDirectory: File, target: String)(implicit
      conv: FileConverter
  ): Seq[(PluginCompat.FileRef, String)] =
    contentOf(baseDirectory, target, (_: File) => true)

  def contentOf(
      baseDirectory: File,
      target: String,
      filter: File => Boolean
  )(implicit conv: FileConverter): Seq[(PluginCompat.FileRef, String)] = {
    (PathFinder(baseDirectory).allPaths --- PathFinder(baseDirectory))
      .filter(filter)
      .pair { (f: File) =>
        IO.relativize(baseDirectory, f).map(p => target.stripSuffix("/") + "/" + p)
      }
      .map { case (f, s) => PluginCompat.toFileRef(f) -> s }
  }

  /**
   * Create mappings from your classpath. For example if you want to add additional dependencies, like test or model.
   *
   * @example
   *   Add all test artifacts to a separated test folder
   *   {{{
   * mappings in Universal ++= fromClasspath((managedClasspath in Test).value, target = "test")
   *   }}}
   *
   * @param entries
   * @param target
   * @return
   *   a list of mappings
   */
  def fromClasspath(
      entries: Seq[Attributed[PluginCompat.FileRef]],
      target: String
  ): Seq[(PluginCompat.FileRef, String)] =
    fromClasspath(entries, target, _ => true)

  /**
   * Create mappings from your classpath. For example if you want to add additional dependencies, like test or model.
   * You can also filter the artifacts that should be mapped to mappings.
   *
   * @example
   *   Filter all osgi bundles
   *   {{{
   * mappings in Universal ++= fromClasspath(
   *    (managedClasspath in Runtime).value,
   *    "osgi",
   *    artifact => artifact.`type` == "bundle"
   * )
   *   }}}
   *
   * @param entries
   *   from where mappings should be created from
   * @param target
   *   folder, e.g. `model`. Must not end with a slash
   * @param includeArtifact
   *   function to determine if an artifact should result in a mapping
   * @param includeOnNoArtifact
   *   default is false. When there's no Artifact meta data remove it
   */
  def fromClasspath(
      entries: Seq[Attributed[PluginCompat.FileRef]],
      target: String,
      includeArtifact: PluginCompat.IncludeArtifact,
      includeOnNoArtifact: Boolean = false
  ): Seq[(PluginCompat.FileRef, String)] =
    entries.filter(attr => attr.get(PluginCompat.artifactStr).map(includeArtifact) getOrElse includeOnNoArtifact).map {
      attribute =>
        val file = attribute.data
        val name = PluginCompat.getName(file)
        file -> s"$target/$name"
    }
}
