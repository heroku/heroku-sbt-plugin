val stage = taskKey[Unit]("build for production")
val createApp = taskKey[Unit]("create a heroku app for tests")
val check = taskKey[Unit]("check test results")
val cleanup = taskKey[Unit]("clean up after tests")

lazy val remoteAppName = "sbt-heroku-" + sys.props("heroku.uuid")

lazy val root = (project in file("."))
  .settings(
    organization in ThisBuild := "com.example",
    scalaVersion in ThisBuild := "2.12.2",
    version      in ThisBuild := "0.1.0-SNAPSHOT",
    name := "Hello",
    test in assembly := {},
    mainClass in assembly := Some("example.Server"),
    assemblyJarName in assembly := "hello.jar",
    assemblyMergeStrategy in assembly := {
      case "META-INF/io.netty.versions.properties" => MergeStrategy.first
      case "BUILD" => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    libraryDependencies ++= Seq(
      "com.twitter" % "finagle-http_2.12" % "6.43.0"
    ),
    herokuAppName in Compile := remoteAppName,
    herokuFatJar in Compile := Some((assemblyOutputPath in assembly).value),
    herokuConfigVars in Compile := Map(
      "MY_VAR" -> "monkeys with a y",
      "JAVA_OPTS" -> "-Xmx384m -Xss512k -DmyVar=monkeys"
    ),
    stage := {
      assembly.value
    },
    createApp := {
      sys.process.Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.value.log
      sys.process.Process("heroku", Seq("create", "-n", remoteAppName)) ! streams.value.log
    },
    cleanup := {
      sys.process.Process("heroku", Seq("apps:destroy", "-a", remoteAppName, "--confirm", remoteAppName)) ! streams.value.log
    },
    check := {
      val config = sys.process.Process("heroku", Seq("config", "-a", remoteAppName)).!!
      if (!(config.contains("MY_VAR") && config.contains("monkeys with a y"))) {
        sys.error("Custom config variable was not set!")
      }
      if (!(config.contains("JAVA_OPTS") && config.contains("-Xmx384m -Xss512k -DmyVar=monkeys"))) {
        sys.error("Default config variable was not overridden!")
      }
    }
  )