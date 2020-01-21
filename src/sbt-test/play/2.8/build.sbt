name := """2.8"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.1"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test

lazy val remoteAppName = "sbt-heroku-" + sys.props("heroku.uuid")

Compile / herokuAppName := remoteAppName

import scala.sys.process._

val cleanup = taskKey[Unit]("Destroys the app used for testing")
val createApp = taskKey[Unit]("Create an app for testing")
val check = taskKey[Unit]("Checks if the testing app works correctly")

cleanup := {
  s"heroku apps:destroy -a ${remoteAppName} --confirm ${remoteAppName} "  ! streams.value.log
}

createApp := {
  cleanup.value
  s"heroku create -n $remoteAppName" ! streams.value.log
}

check := {
  val maxReties = 10
  var retries = 0
  while (retries < maxReties) {
    try {
      val sb = new StringBuilder
      for (line <- scala.io.Source.fromURL("https://" + remoteAppName + ".herokuapp.com").getLines())
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
