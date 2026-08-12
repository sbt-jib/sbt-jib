package de.gccc.jib

import java.io.File
import java.nio.file.Files
import java.util.zip.{ ZipEntry, ZipOutputStream }

import sbt._
import sbtcompat.PluginCompat._

import scala.jdk.CollectionConverters._

class SbtLayerConfigurationsTest extends munit.FunSuite {

  test("dependency jars sharing a file name are not overwritten in /app/libs") {
    val target = Files.createTempDirectory("jib-layers").toFile
    val first  = jar(new File(target, "first/dependency.jar"), entries = 1)
    val second = jar(new File(target, "second/dependency.jar"), entries = 2)

    val libs = layerEntries(target, external = Seq(first, second), name = "libs")

    assertEquals(
      libs.sorted,
      List(s"/app/libs/dependency-${first.length}.jar", s"/app/libs/dependency-${second.length}.jar").sorted
    )
  }

  test("dependency jars with unique file names keep their name") {
    val target = Files.createTempDirectory("jib-layers").toFile
    val first  = jar(new File(target, "first/one.jar"), entries = 1)
    val second = jar(new File(target, "second/two.jar"), entries = 2)

    val libs = layerEntries(target, external = Seq(first, second), name = "libs")

    assertEquals(libs.sorted, List("/app/libs/one.jar", "/app/libs/two.jar"))
  }

  private def layerEntries(target: File, external: Seq[File], name: String): List[String] =
    SbtLayerConfigurations(
      targetDirectory = target,
      classes = Nil,
      resourceDirectories = Nil,
      resources = Nil,
      internalDependencies = Nil,
      external = external.map(file => Attributed.blank(file).put(artifactStr, artifactToStr(Artifact(name)))),
      extraMappings = Nil,
      extraMappingPermissions = Nil,
      specialResourceDirectory = new File(target, "special"),
      mappings = Nil,
      addToClasspath = Nil
    ).generate.flatMap(_.getEntries.asScala.map(_.getExtractionPath.toString)).filter(_.startsWith("/app/libs/"))

  /** A jar is only distinguishable by size here, so vary the number of entries. */
  private def jar(target: File, entries: Int): File = {
    Files.createDirectories(target.toPath.getParent)
    val out = new ZipOutputStream(Files.newOutputStream(target.toPath))
    try {
      (1 to entries).foreach { index =>
        out.putNextEntry(new ZipEntry(s"de/gccc/Class$index.class"))
        out.write(Array.fill(index * 128)(0.toByte))
        out.closeEntry()
      }
    } finally out.close()
    target
  }

}
