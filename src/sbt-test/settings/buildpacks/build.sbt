import NativePackagerKeys._

packageArchetype.java_application

name := """scala-getting-started"""

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "com.twitter" % "finagle-http_2.10" % "6.18.0"
)

lazy val remoteAppName = "sbt-heroku-" + sys.props("heroku.uuid")

herokuJdkVersion in Compile := "1.8"

herokuAppName in Compile := remoteAppName

herokuBuildpacks in Compile := Seq(
  "https://github.com/ryandotsmith/null-buildpack",
  "jvm-common"
)

mainClass in Compile := Some("com.example.Server")

herokuProcessTypes in Compile := Map(
  "web" -> "target/universal/stage/bin/scala-getting-started -Dtest.var=monkeys -Dhttp.port=$PORT",
  "https" -> "target/universal/stage/bin/scala-getting-started -main com.example.Https"
)

TaskKey[Unit]("createApp") <<= (packageBin in Universal, streams) map { (zipFile, streams) =>
  Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.log
  Process("heroku", Seq("create", "-s", "cedar-14", "-n", remoteAppName)) ! streams.log
}

TaskKey[Unit]("cleanup") <<= (packageBin in Universal, streams) map { (zipFile, streams) =>
  Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.log
}

TaskKey[Unit]("check") <<= (packageBin in Universal, streams) map { (zipFile, streams) =>
  var retries = 0
  while (retries < 10) {
    try {
      val sb = new StringBuilder
      for (line <- scala.io.Source.fromURL("https://" + remoteAppName + ".herokuapp.com").getLines())
        sb.append(line).append("\n")
      val page = sb.toString()
      if (!page.contains("Hello from Scala on JDK 1.8"))
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

TaskKey[Unit]("https") <<= (packageBin in Universal, streams) map { (zipFile, streams) =>
  val output = Process("heroku", Seq("run", "https", "-a", remoteAppName)).!!
  if (!output.contains("Successfully invoked HTTPS service.")) {
    sys.error("Failed to invoke HTTPS service: " + output)
  }
  if (!(output.contains(""""X-Forwarded-Proto": "https"""") || output.contains(""""X-Forwarded-Protocol": "https""""))) {
    sys.error("Invoked service without HTTPS: " + output)
  }
}