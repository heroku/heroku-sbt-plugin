import NativePackagerKeys._

packageArchetype.java_application

name := """scala-getting-started"""

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "com.twitter" % "finagle-http_2.10" % "6.18.0"
)

lazy val remoteAppName = "sbt-heroku-" + sys.props("heroku.uuid")

herokuJdkVersion in Compile := "1.7"

herokuAppName in Compile := remoteAppName

herokuConfigVars in Compile := Map(
  "MY_VAR" -> "monkeys with a y",
  "JAVA_OPTS" -> "-Xmx384m -Xss512k -DmyVar=monkeys"
)

TaskKey[Unit]("createApp") := {
  (packageBin in Universal).value
  Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.value.log
  Process("heroku", Seq("create", "-n", remoteAppName)) ! streams.value.log
}

TaskKey[Unit]("cleanup") := {
  (packageBin in Universal).value
  Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.value.log
}

TaskKey[Unit]("check") := {
  (packageBin in Universal).value
  val config = Process("heroku", Seq("config", "-a", remoteAppName)).!!
  if (!(config.contains("MY_VAR") && config.contains("monkeys with a y"))) {
    sys.error("Custom config variable was not set!")
  }
  if (!(config.contains("JAVA_OPTS") && config.contains("-Xmx384m -Xss512k -DmyVar=monkeys"))) {
    sys.error("Default config variable was not overridden!")
  }
}
