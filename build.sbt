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
    ),
    versionScheme := Some("early-semver")
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
    name               := "sbt-jib",
    crossScalaVersions := List("2.12.20", "3.8.3"),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.11.4"
        case _      => "2.0.0"
      }
    },
    addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.1.0"),
    scriptedLaunchOpts ++= Seq("-Xmx1024M", s"-Dplugin.version=${version.value}"),
    scriptedBufferLog := false
  )
  .dependsOn(jibCommon)
  .enablePlugins(SbtPlugin)

lazy val root = (project in file("."))
  .settings(
    publish / skip     := true,
    crossScalaVersions := Nil
  )
  .aggregate(jibCommon, sbtJib)
