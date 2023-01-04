import xerial.sbt.Sonatype._

sonatypeProfileName    := "de.gccc"
sonatypeProjectHosting := Some(GitHubHosting("sbt-jib", "sbt-jib", "c.schmitt@briefdomain.de"))

inThisBuild(
  Seq(
    scalaVersion := "2.12.15",
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
    ),
    Compile / scalacOptions ++= Seq("-target:jvm-1.8"),
    Compile / Keys.compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
  )
)

lazy val jibCommon = (project in file("jib-common")).settings(
  name               := "jib-common",
  crossScalaVersions := List(scalaVersion.value, "2.11.12", "2.13.10", "3.2.1"),
  libraryDependencies ++= List(
    "com.google.cloud.tools"  % "jib-core"                % "0.23.0",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.9.0",
    "org.scala-lang.modules" %% "scala-java8-compat"      % "1.0.2"
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
