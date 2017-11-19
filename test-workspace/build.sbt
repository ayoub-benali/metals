inThisBuild(
  List(
    scalaVersion := "2.12.4",
    addCompilerPlugin(
      "org.scalameta" % "semanticdb-scalac" % "2.1.1" cross CrossVersion.full
    ),
    libraryDependencies ++= List(
      "com.vladsch.flexmark" % "flexmark-all" % "0.28.6"
    ),
    scalacOptions += "-Yrangepos"
  )
)
lazy val a = project
lazy val b = project.dependsOn(a)