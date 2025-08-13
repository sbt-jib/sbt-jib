val scala212 = "2.12.20"
val scala3   = "3.7.2"

inThisBuild(
  Seq(
    organization       := "de.gccc.sbt",
    scalaVersion       := scala3,
    crossScalaVersions := Seq(scala212, scala3),
    homepage           := Some(url("https://github.com/schmitch")),
    licenses           := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    developers         := List(
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
  name := "jib-common",
  libraryDependencies ++= List(
    "com.google.cloud.tools"  % "jib-core"                % "0.27.3",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.13.0"
  )
)

lazy val sbtJib = (project in file("sbt-jib"))
  .settings(
    name                            := "sbt-jib",
    (pluginCrossBuild / sbtVersion) := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.5.8"
        case _      => "2.0.0-RC3"
      }
    },
    scalacOptions ++= {
      scalaBinaryVersion.value match {
        case "3" =>
          Seq("-deprecation", "-unchecked", "-no-indent")
        case _ =>
          Seq("-deprecation", "-unchecked", "-Xsource:3", "-Xfuture")
      }
    }
  )
  .dependsOn(jibCommon)
  .enablePlugins(SbtPlugin)

lazy val root = (project in file("."))
  .settings(
    publish / skip     := true,
    crossScalaVersions := Nil
  )
  .aggregate(jibCommon, sbtJib)
