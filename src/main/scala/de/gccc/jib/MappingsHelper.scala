package de.gccc.jib

import java.io.File

import sbt._
import sbt.io.{ IO, PathFinder }

import scala.language.postfixOps

/** A set of helper methods to simplify the writing of mappings */
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
   * mappings in Universal ++= sourceDir("extra")
   *   }}}
   *
   * @param sourceDir
   *   as string representation
   * @return
   *   mappings
   */
  def contentOf(sourceDir: String): Seq[(File, String)] =
    contentOf(file(sourceDir))

  def contentOf(baseDirectory: File, target: String): Seq[(File, String)] = {
    contentOf(baseDirectory, target, (_: File) => true)
  }

  def contentOf(
      baseDirectory: File,
      target: String,
      filter: File => Boolean
  ): Seq[(File, String)] = {
    (PathFinder(baseDirectory).allPaths --- PathFinder(baseDirectory))
      .filter(filter)
      .pair((f: File) => {
        IO.relativize(baseDirectory, f).map(p => target.stripSuffix("/") + "/" + p)
      })
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
  def fromClasspath(entries: Seq[Attributed[File]], target: String): Seq[(File, String)] =
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
      entries: Seq[Attributed[File]],
      target: String,
      includeArtifact: Artifact => Boolean,
      includeOnNoArtifact: Boolean = false
  ): Seq[(File, String)] =
    entries.filter(attr => attr.get(sbt.Keys.artifact.key) map includeArtifact getOrElse includeOnNoArtifact).map {
      attribute =>
        val file = attribute.data
        file -> s"$target/${file.getName}"
    }

}
