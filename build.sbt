sbtPlugin := true

name := "sbt-heroku"

organization := "com.heroku"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-compress" % "1.8.1",
  "io.spray" %% "spray-json" % "1.2.6"
)

publishArtifact in (Compile, packageDoc) := false

publishArtifact in (Compile, packageSrc) := false
