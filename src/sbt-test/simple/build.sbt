lazy val hase = (project in file("module"))

lazy val root = (project in file("."))
  .enablePlugins(JibPlugin)
  .settings(
    libraryDependencies += "commons-io" % "commons-io" % "2.6",
    organization := "hase",
    name := "demo-project",
    version := "0.0.1",
    scalaVersion := "2.12.6",
  )


val demo = taskKey[Unit]("simple task")
demo := {
    val _ = (packageBin in Compile).value
    val art = (artifactPath in (Compile, packageBin)).value.getPath
    val external =
        (externalDependencyClasspath or (externalDependencyClasspath in Runtime)).value
    val depJars = (internalDependencyAsJars in Compile).value
    println(s"packageFile: $art")
    println(s"internalDependencyAsJars: $depJars")
    println(s"externalDependencyClasspath: $external")

}