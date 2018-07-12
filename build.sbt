import ReleaseTransformations._
import xerial.sbt.Sonatype._

organization := "de.gccc.sbt"
scalaVersion := "2.12.6"
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

lazy val root = (project in file(".")).settings(
  name := "sbt-jib",
  sbtPlugin := true,
  // Add the default sonatype repository setting
  publishTo := sonatypePublishTo.value,
  unmanagedSourceDirectories in Compile += baseDirectory.value / "jib" / "jib-core" / "src" / "main" / "java",
  unmanagedResourceDirectories in Compile += baseDirectory.value / "jib" / "jib-core" / "src" / "main" / "resources",
  libraryDependencies ++= Seq(
    // These are copied over from jib-core and are necessary for the jib-core sourcesets.
    "com.google.http-client"     % "google-http-client" % "1.23.0",
    "org.apache.commons"         % "commons-compress"   % "1.17",
    "com.google.guava"           % "guava"              % "23.5-jre",
    "com.fasterxml.jackson.core" % "jackson-databind"   % "2.9.6",
    "org.slf4j"                  % "slf4j-api"          % "1.7.25",
    "org.javassist"              % "javassist"          % "3.22.0-GA"
  ),
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
