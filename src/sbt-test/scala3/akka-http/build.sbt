import scala.sys.process._

name := "scala3-getting-started"

version := "1.0"

scalaVersion := "3.1.2"

val AkkaVersion = "2.6.19"
val AkkaHttpVersion = "10.2.9"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion
).map(_.cross(CrossVersion.for3Use2_13))

Compile / mainClass := Some("com.example.Server")

Compile / herokuFatJar := Some((assembly / assemblyOutputPath).value)

lazy val remoteAppName = "sbt-heroku-" + sys.props("heroku.uuid")
Compile / herokuAppName := remoteAppName

TaskKey[Unit]("createApp") := {
  (Compile / packageBin).value
  Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.value.log
  Process("heroku", Seq("create", "-n", remoteAppName)) ! streams.value.log
}

TaskKey[Unit]("cleanup") := {
  (Compile / packageBin).value
  Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.value.log
}

TaskKey[Unit]("check") := {
  (Compile / packageBin).value
  var retries = 0
  while (retries < 10) {
    try {
      val sb = new StringBuilder
      for (line <- scala.io.Source.fromURL("https://" + remoteAppName + ".herokuapp.com/hello").getLines())
        sb.append(line).append("\n")
      val page = sb.toString()
      if (!page.contains("Say hello to akka-http"))
        sys.error("There is a problem with the webpage: " + page)
      retries = 99999
    } catch {
      case ex: Exception =>
        if (retries < 10) {
          println("Error (retrying): " + ex.getMessage)
          Thread.sleep(1000)
          retries += 1
        } else {
          throw ex
        }
    }
    ()
  }
}
