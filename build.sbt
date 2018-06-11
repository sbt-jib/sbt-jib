lazy val hase = (project in file("module"))

organization := "de.gccc.sbt"
scalaVersion := "2.12.6"

lazy val root = (project in file("."))
  .aggregate(hase)
  .dependsOn(hase)
  .settings(
    name := "sbt-jib",
    sbtPlugin := true,
    unmanagedSourceDirectories in Compile += baseDirectory.value / "jib" / "jib-core" / "src" / "main" / "java",
    unmanagedResourceDirectories in Compile += baseDirectory.value / "jib" / "jib-core" / "src" / "main" / "resources",
    doc in Compile := (target.value / "none"),
    libraryDependencies ++= Seq(
      // These are copied over from jib-core and are necessary for the jib-core sourcesets.
      "com.google.http-client" % "google-http-client" % "1.23.0",
      "org.apache.commons" % "commons-compress" % "1.15",
      "com.google.guava" % "guava" % "23.5-jre",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.2",
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "org.javassist" % "javassist" % "3.22.0-GA"
    )
  )
