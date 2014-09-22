name := """sample-play-app"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

//logLevel := Level.Debug

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws
)

lazy val remoteAppName = "sbt-heroku-" + sys.props("heroku.uuid")

herokuJdkVersion in Compile := "1.7"

herokuAppName in Compile := remoteAppName

TaskKey[Unit]("createApp") <<= (packageBin in Universal, streams) map { (zipFile, streams) =>
  Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.log
  Process("heroku", Seq("create", "-n", remoteAppName)) ! streams.log
}

TaskKey[Unit]("cleanup") <<= (packageBin in Universal, streams) map { (zipFile, streams) =>
  Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.log
}

TaskKey[Unit]("check") <<= (packageBin in Universal, streams) map { (zipFile, streams) =>
  val maxReties = 10
  var retries = 0
  while (retries < maxReties) {
    try {
      val sb = new StringBuilder
      for (line <- scala.io.Source.fromURL("http://" + remoteAppName + ".herokuapp.com").getLines())
        sb.append(line).append("\n")
      val page = sb.toString()
      if (!page.contains("Welcome to Play"))
        sys.error("There is a problem with the webpage: " + page)
      retries = maxReties
    } catch {
      case ex: Exception =>
        if (retries < maxReties-1) {
          Thread.sleep(1000)
          retries += 1
        } else {
          throw ex
        }
    }
    ()
  }
}