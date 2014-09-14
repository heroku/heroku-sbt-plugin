import NativePackagerKeys._

packageArchetype.java_application

name := """scala-getting-started"""

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "com.twitter" % "finagle-http_2.10" % "6.18.0"
)

lazy val remoteAppName = "some-app-no-exists-32nj14"

herokuJdkVersion in Compile := "1.7"

herokuAppName in Compile := remoteAppName

TaskKey[Unit]("check") <<= (packageBin in Universal, streams) map { (zipFile, streams) =>
  var retries = 0
  while (retries < 10) {
    try {
      val sb = new StringBuilder
      for (line <- scala.io.Source.fromURL("http://" + remoteAppName + ".herokuapp.com").getLines())
        sb.append(line).append("\n")
      val page = sb.toString()
      if (!page.contains("Hello from Scala on JDK 1.7"))
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