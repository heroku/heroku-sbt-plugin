name := """scala-getting-started"""

version := "1.0"

scalaVersion := "2.10.4"

mainClass in Compile := Some("com.example.Server")

libraryDependencies ++= Seq(
  "com.twitter" % "finagle-http_2.10" % "6.18.0",
  "postgresql" % "postgresql" % "9.0-801.jdbc4"
)

herokuFatJar in Compile := Some((assemblyOutputPath in assembly).value)

lazy val remoteAppName = "sbt-heroku-" + sys.props("heroku.uuid")

herokuAppName in Compile := remoteAppName

TaskKey[Unit]("createApp") := {
  (packageBin in Compile).value
  Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.value.log
  Process("heroku", Seq("create", "-s", "cedar-14", "-n", remoteAppName)) ! streams.value.log
}

TaskKey[Unit]("cleanup") := {
  (packageBin in Compile).value
  Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.value.log
}

TaskKey[Unit]("check") := {
  (packageBin in Compile).value
  var retries = 0
  while (retries < 10) {
    try {
      val sb = new StringBuilder
      for (line <- scala.io.Source.fromURL("https://" + remoteAppName + ".herokuapp.com").getLines())
        sb.append(line).append("\n")
      val page = sb.toString()
      if (!page.contains("Hello from Scala"))
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
