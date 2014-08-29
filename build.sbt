import bintray.Keys._

sbtPlugin := true

name := "sbt-heroku"

organization := "com.heroku"

scalaVersion in Global := "2.10.4"

libraryDependencies ++= Seq(
  "io.spray" %% "spray-json" % "1.2.6"
)

scriptedSettings

scriptedLaunchOpts <+= version apply { v => "-Dproject.version="+v }

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-XX:MaxPermSize=256M",
    "-Dheroku.uuid=" + java.util.UUID.randomUUID.toString.substring(0,15))
}

publishMavenStyle := false

bintraySettings

repository in bintray := "sbt-plugins"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

bintrayOrganization in bintray := Some("heroku")