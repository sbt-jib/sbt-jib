package de.gccc.jib

import sbt.{ Def, _ }
import sbt.Keys._

object JibPlugin extends AutoPlugin {

  object autoImport {
    val jibDockerBuild = taskKey[Unit]("jib build docker image")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    jibDockerBuild := {

    }
  )

}
