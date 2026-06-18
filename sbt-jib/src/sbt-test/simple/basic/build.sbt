ThisBuild / scalaVersion := "2.12.20"

lazy val hase = project in file("module")

lazy val root = (project in file("."))
  .enablePlugins(JibPlugin)
  .dependsOn(hase)
  .settings(
    organization := "schmitch",
    name         := "demo-project",
    version      := "0.0.2",
    libraryDependencies += "commons-io" % "commons-io" % "2.6",
    // busybox: tiny multi-arch base, fast to pull; tar build needs no daemon/registry
    jibBaseImage := "busybox:latest"
  )
