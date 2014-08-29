sbtPlugin := true

name := "sbt-heroku"

organization := "com.heroku"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "io.spray" %% "spray-json" % "1.2.6"
)

scriptedSettings

scriptedLaunchOpts <+= version apply { v => "-Dproject.version="+v }

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-XX:MaxPermSize=256M",
    "-Dheroku.uuid=" + java.util.UUID.randomUUID.toString.substring(0,15))
}

Release.settings

publishArtifact in (Compile, packageDoc) := false

publishArtifact in (Compile, packageSrc) := false
