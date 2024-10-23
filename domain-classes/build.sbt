name := "joern-benchmarks-domain-classes"

libraryDependencies += "io.shiftleft" %% "codepropertygraph" % Versions.cpg

lazy val generatedSrcDir = settingKey[File]("root for generated sources - we want to check those in")
generatedSrcDir := (Compile/sourceDirectory).value / "generated"

Compile/unmanagedSourceDirectories += generatedSrcDir.value
Compile/compile := (Compile/compile).dependsOn(Projects.schema/Compile/generateDomainClasses).value

Compile / scalacOptions --= Seq("-Xfatal-warnings", "-Wunused", "-Ywarn-unused")
