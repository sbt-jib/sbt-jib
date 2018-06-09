lazy val hase = (project in file("module"))

organization := "com.example"
scalaVersion := "2.12.6"

lazy val root = (project in file("."))
  .aggregate(hase)
  .dependsOn(hase)
  .settings(
    name := "sbt-jib",
    sbtPlugin := true,
    libraryDependencies += "com.google.cloud" % "jib-core" % "1-ENVISIA", // jib-core LOCAL ONLY
    libraryDependencies += "com.google.guava" % "guava" % "25.1-jre"
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
// :point_up: [29. Mai 2018 10:20](https://gitter.im/sbt/sbt?at=5b0d0d3f352b9e1a4b3eee20)

/*

def makeJarName(org: String,
    name: String,
    revision: String,
    artifactName: String,
    artifactClassifier: Option[String]): String =
  org + "." +
      name + "-" +
      Option(artifactName.replace(name, "")).filterNot(_.isEmpty).map(_ + "-").getOrElse("") +
      revision +
      artifactClassifier.filterNot(_.isEmpty).map("-" + _).getOrElse("") +
      ".jar"

def isRuntimeArtifact(dep: Attributed[File]): Boolean =
  dep.get(sbt.Keys.artifact.key).map(_.`type` == "jar").getOrElse {
    val name = dep.data.getName
    !(name.endsWith(".jar") || name.endsWith("-sources.jar") || name.endsWith("-javadoc.jar"))
  }

def dependencyProjectRefs(build: sbt.internal.BuildDependencies, thisProject: ProjectRef): Seq[ProjectRef] =
  build.classpathTransitive.getOrElse(thisProject, Nil)

def findProjectDependencyArtifacts: Def.Initialize[Task[Seq[Attributed[File]]]] =
  Def
      .task {
        val stateTask = state.taskValue
        val refs = thisProjectRef.value +: dependencyProjectRefs(buildDependencies.value, thisProjectRef.value)
        // Dynamic lookup of dependencies...
        val artTasks = refs map { ref =>
          extractArtifacts(stateTask, ref)
        }
        val allArtifactsTask: Task[Seq[Attributed[File]]] =
          artTasks.fold[Task[Seq[Attributed[File]]]](task(Nil)) { (previous, next) =>
            for {
              p <- previous
              n <- next
            } yield p ++ n.filter(isRuntimeArtifact)
          }
        allArtifactsTask
      }
      .flatMap(identity)

def extractArtifacts(stateTask: Task[State], ref: ProjectRef): Task[Seq[Attributed[File]]] =
  stateTask.flatMap { state =>
    val extracted = Project.extract(state)
    // TODO - Is this correct?
    val module = extracted.get(projectID in ref)
    val artifactTask = extracted.get(packagedArtifacts in ref)
    for {
      arts <- artifactTask
    } yield {
      for {
        (art, file) <- arts.toSeq // TODO -Filter!
      } yield Attributed.blank(file).put(moduleID.key, module).put(artifact.key, art)
    }
  }

def getJarFullFilename(dep: Attributed[File]): String = {
  val filename: Option[String] = for {
    module <- dep.metadata
        // sbt 0.13.x key
        .get(AttributeKey[ModuleID]("module-id"))
        // sbt 1.x key
        .orElse(dep.metadata.get(AttributeKey[ModuleID]("moduleID")))
    artifact <- dep.metadata.get(AttributeKey[Artifact]("artifact"))
  } yield makeJarName(module.organization, module.name, module.revision, artifact.name, artifact.classifier)
  filename.getOrElse(dep.data.getName)
}

def findRealDep(dep: Attributed[File], projectArts: Seq[Attributed[File]]): Option[Attributed[File]] =
  if (dep.data.isFile) Some(dep)
  else {
    projectArts.find { art =>
      // TODO - Why is the module not showing up for project deps?
      //(art.get(sbt.Keys.moduleID.key) ==  dep.get(sbt.Keys.moduleID.key)) &&
      (art.get(sbt.Keys.artifact.key), dep.get(sbt.Keys.artifact.key)) match {
        case (Some(l), Some(r)) =>
          // TODO - extra attributes and stuff for comparison?
          // seems to break stuff if we do...
          l.name == r.name && l.classifier == r.classifier
        case _ => false
      }
    }
  }

def universalDepMappings(deps: Seq[Attributed[File]],
    projectArts: Seq[Attributed[File]]): Seq[(File, String)] =
  for {
    dep <- deps
    realDep <- findRealDep(dep, projectArts)
  } yield realDep.data -> ("lib/" + getJarFullFilename(realDep))

val printDeps2 = taskKey[Unit]("A sample string task.")
printDeps2 := {
  val cmds = dockerCommands.value
  println(Dockerfile(cmds: _*).makeContent)
}
val printDeps = taskKey[Unit]("A sample string task.")
printDeps := {

  // Script Classpath 1
  val _ = (packageBin in Compile).value
  //  val id = projectID.value
  //  val art = (artifact in Compile in packageBin).value
  //  val x1 = jar -> ("lib/" + makeJarName(id.organization, id.name, id.revision, art.name, art.classifier))

  val depArts = findProjectDependencyArtifacts.value

  // Script Classpath 2
  //  val x2 = universalDepMappings((dependencyClasspath in Runtime).value, depArts)

  //  val x = (packageBin in Compile).value
  //  println(s"Package: $x")
  //  val fullDeps = (fullClasspath or (fullClasspath in Runtime)).value

  //
  //  val dependencyDeps = (dependencyClasspath in Runtime).value
  //  println(s"Runtime Deps: $runtimeDeps")
  //  println(s"Full Deps: $fullDeps")
  //
  //  val s = (streams in assembly).value
  //
  //  val classpath = runtimeDeps
  //  val (libs, dirs) = classpath.toVector.partition(c => ClasspathUtilities.isArchive(c.data))
  //
  //
  //  println(s"CL: $dependencyDeps")
  val runtimeDeps =
  (externalDependencyClasspath or (externalDependencyClasspath in Runtime)).value
  // internalDependencyClasspath
  val out = (target in Compile).value / "docker-stage"
  Files.createDirectory(out.toPath)
  val stage1 = out / "stage1"
  val stage2 = out / "stage2"
  val stage1Path = stage1.toPath
  val stage2Path = stage2.toPath
  Files.createDirectory(stage1Path)
  Files.createDirectory(stage2Path)

  runtimeDeps.foreach { a =>
    val p = a.data.toPath
    Files.copy(p, stage1Path.resolve(p.getFileName))
  }

  depArts.foreach { a =>
    val p = a.data.toPath
    Files.copy(p, stage2Path.resolve(p.getFileName))
  }

  println(s"Build Deps: $depArts")
  println("")
  println("")
  println(s"Runtime Dep: $runtimeDeps")
  println("")
  println("")
  println(s"Artifacts: $out")
}
 */
