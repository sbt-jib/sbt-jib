lazy val hase = (project in file("module"))

lazy val root = (project in file("."))
  .enablePlugins(SbtTwirl, JibPlugin)
  .settings(
    libraryDependencies += "commons-io" % "commons-io" % "2.6",
    organization := "schmitch",
    name := "demo-project",
    version := "0.0.2",
    scalaVersion := "2.12.6",
    sourceDirectories in (Compile, TwirlKeys.compileTemplates) := (unmanagedSourceDirectories in Compile).value,
    mappings in Jib := (baseDirectory.value / "dist" / "x.txt" -> "hase/out.txt") :: Nil
  ).dependsOn(hase)


val demo = taskKey[Unit]("simple task")
demo := {
    val _ = (compile in Compile).value
    val art = (artifactPath in (Compile, packageBin)).value.getPath
    val external =
        (externalDependencyClasspath or (externalDependencyClasspath in Runtime)).value
    val depJars = (internalDependencyClasspath in Compile).value
    val internal = (internalDependencyClasspath or (internalDependencyClasspath in Runtime)).value
    println(s"packageFile: $art")
    println(s"internalDependencyClasspath: $internal")
    println(s"internalDependencyClasspath: $depJars")
    println(s"externalDependencyClasspath: $external")

}