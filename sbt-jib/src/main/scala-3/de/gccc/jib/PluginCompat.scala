package de.gccc.jib

import sbt.*
import sbt.internal.librarymanagement.ivy.IvyCredentials
import xsbti.{ FileConverter, HashedVirtualFileRef, VirtualFileRef }

import java.io.File
import java.nio.file.{ Files, Path as NioPath }

private[jib] object PluginCompat {

  type Credentials     = sbt.librarymanagement.Credentials
  type Classpath       = Seq[Attributed[HashedVirtualFileRef]]
  type FileRef         = HashedVirtualFileRef
  type ArtifactPath    = VirtualFileRef
  type IncludeArtifact = Any => Boolean

  final val CollectionConverters = scala.jdk.CollectionConverters

  val artifactStr = Keys.artifactStr

  def getName(ref: FileRef): String = ref.name()

  def isFile(ref: FileRef)(using conv: FileConverter): Boolean = Files.isRegularFile(toNioPath(ref))

  def toNioPath(a: Attributed[HashedVirtualFileRef])(using conv: FileConverter): NioPath = conv.toPath(a.data)

  def toNioPath(ref: HashedVirtualFileRef)(using conv: FileConverter): NioPath = conv.toPath(ref)

  def toFile(a: Attributed[HashedVirtualFileRef])(using conv: FileConverter): File = toNioPath(a).toFile()

  def toFile(ref: HashedVirtualFileRef)(using conv: FileConverter): File = toNioPath(ref).toFile()

  def toNioPaths(cp: Seq[Attributed[HashedVirtualFileRef]])(using conv: FileConverter): List[NioPath] =
    cp.map(toNioPath).toList

  def toFileRef(x: File)(using conv: FileConverter): FileRef = conv.toVirtualFile(x.toPath())

  def credsForHost(credentials: Seq[Credentials])(host: String): Option[(String, String)] =
    IvyCredentials.forHost(credentials, host).map(c => (c.userName, c.passwd))

}
