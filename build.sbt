import xerial.sbt.Sonatype._

sonatypeProfileName    := "de.gccc"
sonatypeProjectHosting := Some(GitHubHosting("sbt-jib", "sbt-jib", "c.schmitt@briefdomain.de"))

inThisBuild(
  Seq(
    scalaVersion := "2.12.20",
    organization := "de.gccc.sbt",
    homepage     := Some(url("https://github.com/schmitch")),
    licenses     := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    developers := List(
      Developer(
        id = "schmitch",
        name = "Christian Schmitt",
        email = "c.schmitt@briefdomain.de",
        url = url("https://github.com/schmitch")
      )
    )
  )
)

lazy val jibCommon = (project in file("jib-common")).settings(
  name               := "jib-common",
  crossScalaVersions := List(scalaVersion.value, "2.11.12", "2.13.16", "3.3.6"),
  libraryDependencies ++= List(
    "com.google.cloud.tools"  % "jib-core"                % "0.27.3",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.9.0"
  )
)

lazy val sbtJib = (project in file("sbt-jib"))
  .settings(
    name := "sbt-jib"
  )
  .dependsOn(jibCommon)
  .enablePlugins(SbtPlugin)

lazy val root = (project in file("."))
  .settings(
    publish / skip     := true,
    crossScalaVersions := Nil
  )
  .aggregate(jibCommon, sbtJib)
