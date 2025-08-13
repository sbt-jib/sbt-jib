package de.gccc.jib

import sbt.*
import sbt.librarymanagement.ivy
import scala.annotation.{ meta, StaticAnnotation }
import xsbti.FileConverter

import java.nio.file.{ Path => NioPath }

private[jib] object PluginCompat {

  type Credentials     = ivy.Credentials
  type Classpath       = Seq[Attributed[File]]
  type FileRef         = java.io.File
  type ArtifactPath    = java.io.File
  type IncludeArtifact = Artifact => Boolean

  val artifactStr = Keys.artifact.key

  final val CollectionConverters = scala.collection.JavaConverters

  def getName(ref: File): String = ref.getName()

  def isFile(ref: FileRef)(implicit conv: FileConverter): Boolean = ref.isFile

  def toNioPath(a: Attributed[File])(implicit conv: FileConverter): NioPath = a.data.toPath()

  def toNioPath(ref: File)(implicit conv: FileConverter): NioPath = ref.toPath()

  def toFile(a: Attributed[File])(implicit conv: FileConverter): File = a.data

  def toFile(ref: File)(implicit conv: FileConverter): File = ref

  def toFileRef(x: File)(implicit conv: FileConverter): FileRef = x

  def toNioPaths(cp: Seq[Attributed[File]])(implicit conv: FileConverter): List[NioPath] =
    cp.map(_.data.toPath()).toList

  def credsForHost(credentials: Seq[Credentials])(host: String): Option[(String, String)] =
    ivy.Credentials.forHost(credentials, host).map(c => (c.userName, c.passwd))

  def uncached[A](a: A): A = a

  implicit class DefOp(singleton: Def.type) {
    def uncached[A1](a: A1): A1 = a
  }

  @meta.getter
  class cacheLevel(include: Array[String]) extends StaticAnnotation

}
