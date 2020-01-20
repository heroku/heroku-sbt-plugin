import com.typesafe.sbt.SbtStartScript

name := "sbt-start-script-sample"

organization := "com.example"

version := "0.1"

scalaVersion := "2.10.7"

seq(SbtStartScript.startScriptForClassesSettings: _*)

libraryDependencies ++= Seq(
  "com.twitter"          % "finagle-http_2.10" % "6.18.0"
)

herokuJdkVersion in Compile := "1.7"

herokuAppName in Compile := "sbt-heroku"
