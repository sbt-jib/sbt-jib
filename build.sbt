import ReleaseTransformations._
import xerial.sbt.Sonatype._

organization := "de.gccc.sbt"
scalaVersion := "2.12.12"
sonatypeProfileName := "de.gccc"
publishMavenStyle := true
licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

sonatypeProjectHosting := Some(GitHubHosting("schmitch", "sbt-jib", "c.schmitt@briefdomain.de"))
homepage := Some(url("https://github.com/schmitch"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/schmitch/sbt-jib"),
    "scm:git@github.com:schmitch/sbt-jib.git"
  )
)
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
    Compile / Keys.compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
  )
)

lazy val root = (project in file("."))
  .settings(
    name := "sbt-jib",
    // Add the default sonatype repository setting
    publishTo := sonatypePublishTo.value,
    libraryDependencies += "com.google.cloud.tools" % "jib-core" % "0.18.0",
    releaseCrossBuild := true, // true if you cross-build the project for multiple Scala versions
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      // For non cross-build projects, use releaseStepCommand("publishSigned")
      releaseStepCommandAndRemaining("+publishSigned"),
      setNextVersion,
      commitNextVersion,
      releaseStepCommand("sonatypeReleaseAll"),
      pushChanges
    )
  )
  .enablePlugins(SbtPlugin)
