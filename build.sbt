import bintray.Keys._

sbtPlugin := true

name := "sbt-heroku"

organization := "com.heroku"

scalaVersion in Global := "2.10.4"

scalacOptions in Compile += "-deprecation"

libraryDependencies ++= Seq(
  "com.heroku.sdk" % "heroku-deploy" % "0.3.1"
)

scriptedSettings

scriptedLaunchOpts <+= version apply { v => "-Dproject.version="+v }

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-XX:MaxPermSize=256M",
    "-Dheroku.uuid=" + java.util.UUID.randomUUID.toString.substring(0,15))
}

publishMavenStyle := false

bintrayPublishSettings

repository in bintray := "sbt-plugins"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

bintrayOrganization in bintray := Some("heroku")

Release.settings