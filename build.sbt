ThisBuild / tlBaseVersion := "2.1"

lazy val `sbt-heroku` = project in file(".")

name := "sbt-heroku"

enablePlugins(SbtPlugin)

licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))

ThisBuild / crossScalaVersions := Seq("2.12.17")
libraryDependencies ++= Seq(
  "com.heroku.sdk" % "heroku-deploy" % "2.0.16"
)

tlFatalWarnings := false

Test / test := {
  scripted.toTask("").value
}

// Scripted
scriptedLaunchOpts ++= Seq("-Dproject.version=" + version.value,
                           "-Dheroku.uuid=" + java.util.UUID.randomUUID.toString.substring(0, 15)
)
