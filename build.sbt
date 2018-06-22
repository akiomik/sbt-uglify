organization := "com.github.akiomik"
name := "sbt-uglify-es"
description := "sbt-web plugin for minifying JavaScript files"
addSbtJsEngine("1.2.2")
libraryDependencies ++= Seq(
  "org.webjars.npm" % "uglify-es" % "3.3.10",
  "io.monix" %% "monix" % "2.3.0"
)

//scriptedBufferLog := false
