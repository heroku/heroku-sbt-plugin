import NativePackagerKeys._

packageArchetype.java_application

name := """scala-getting-started"""

version := "1.0"

scalaVersion := "2.10.7"

libraryDependencies ++= Seq(
  "com.twitter" % "finagle-http_2.10" % "6.18.0"
)

lazy val remoteAppName = "some-app-no-exists-32nj14"

herokuJdkVersion in Compile := "1.7"

herokuAppName in Compile := remoteAppName
