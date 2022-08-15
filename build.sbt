import xerial.sbt.Sonatype._

scalaVersion := "2.12.15"

organization := "de.gccc.sbt"
sonatypeProfileName := "de.gccc"
homepage := Some(url("https://github.com/schmitch"))
licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
sonatypeProjectHosting := Some(GitHubHosting("schmitch", "sbt-jib", "c.schmitt@briefdomain.de"))
developers := List(
  Developer(
    id = "schmitch",
    name = "Christian Schmitt",
    email = "c.schmitt@briefdomain.de",
    url = url("https://github.com/schmitch")
  )
)

inThisBuild(
  Seq(
    Compile / scalacOptions ++= Seq("-target:jvm-1.8"),
    Compile / Keys.compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  )
)

lazy val root = (project in file("."))
  .settings(
    name := "sbt-jib",
    libraryDependencies += "com.google.cloud.tools" % "jib-core" % "0.21.0",
  )
  .enablePlugins(SbtPlugin)
