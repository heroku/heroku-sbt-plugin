package com.heroku.sbt

import sbt.Keys._
import sbt._

import scala.collection.JavaConversions

object HerokuPlugin extends AutoPlugin {

  object autoImport {

    val deployHeroku = taskKey[Unit]("Deploy to Heroku.")
    val deployHerokuSlug = taskKey[Unit]("Deploy to Heroku.")
    val herokuJdkVersion = settingKey[String]("Set the major version of the JDK to use.")
    val herokuAppName = settingKey[String]("Set the name of the Heroku application.")
    val herokuConfigVars = settingKey[Map[String,String]]("Config variables to set on the Heroku application.")
    val herokuJdkUrl = settingKey[String]("The location of the JDK binary.")
    val herokuProcessTypes = settingKey[Map[String,String]]("The process types to run on Heroku (similar to Procfile).")
    val herokuIncludePaths = settingKey[Seq[String]]("A list of directory paths to include in the slug.")
    val herokuStack = settingKey[String]("The Heroku runtime stack.")
    val herokuSkipSubProjects = settingKey[Boolean]("Instructs the plugin to skip sub-projects if true (default is true).")
    val herokuBuildpacks = settingKey[Seq[String]]("A list of buildpacks that will be run against the deployment package.")
    val herokuFatJar = settingKey[Option[File]]("The path to a Fat JAR to deploy.")

    lazy val baseHerokuSettings: Seq[Def.Setting[_]] = Seq(
      deployHeroku := {
        // TODO this should be able to detect sub-projects in a standard way, and filter sub-projects so that
        // some could be built and some could be skipped
        if ((baseDirectory.value / "project").exists || !(herokuSkipSubProjects in deployHeroku).value) {
          val includedFiles = JavaConversions.seqAsJavaList((herokuIncludePaths in deployHeroku).value.map {
            case path: String => new java.io.File(path)
          })
          val configVars = JavaConversions.mapAsJavaMap((herokuConfigVars in deployHeroku).value)
          val processTypes = JavaConversions.mapAsJavaMap((herokuProcessTypes in deployHeroku).value)
          val jdkVersion = (herokuJdkVersion in deployHeroku).value
          val stack = (herokuStack in deployHeroku).value
          val buildpacks = JavaConversions.seqAsJavaList((herokuBuildpacks in deployHeroku).value)
          val fatjar = (herokuFatJar in deployHeroku).value
          new SbtApp("sbt-heroku", (herokuAppName in deployHeroku).value, baseDirectory.value, target.value, buildpacks, fatjar, streams.value.log).
            deploy(includedFiles, configVars, jdkVersion, stack, processTypes, "slug.tgz")
        }
      },
      deployHerokuSlug := {
        // TODO this should be able to detect sub-projects in a standard way, and filter sub-projects so that
        // some could be built and some could be skipped
        if ((baseDirectory.value / "project").exists || !(herokuSkipSubProjects in deployHeroku).value) {
          val includedFiles = JavaConversions.seqAsJavaList((herokuIncludePaths in deployHeroku).value.map {
            case path: String => new java.io.File(path)
          })
          val configVars = JavaConversions.mapAsJavaMap((herokuConfigVars in deployHeroku).value)
          val processTypes = JavaConversions.mapAsJavaMap((herokuProcessTypes in deployHeroku).value)
          val stack = (herokuStack in deployHeroku).value
          val fatjar = (herokuFatJar in deployHeroku).value
          val sbtApp =  new SbtApp("sbt-heroku", (herokuAppName in deployHeroku).value, baseDirectory.value, target.value, JavaConversions.seqAsJavaList(Seq()), fatjar, streams.value.log)
          if ((herokuJdkUrl in deployHeroku).value.isEmpty) {
            val jdkVersion : String = (herokuJdkVersion in deployHeroku).value
            sbtApp.deploySlug(includedFiles, configVars, jdkVersion, stack, processTypes, "slug.tgz")
          } else {
            val jdkUrl : URL = new URL((herokuJdkUrl in deployHeroku).value)
            sbtApp.deploySlug(includedFiles, configVars, jdkUrl, stack, processTypes, "slug.tgz")
          }
        }
      },
      herokuJdkVersion in Compile := "1.8",
      herokuAppName in Compile := "",
      herokuConfigVars in Compile := Map[String,String](),
      herokuProcessTypes in Compile := Map[String,String](),
      herokuJdkUrl in Compile := "",
      herokuStack in Compile := "cedar-14",
      herokuIncludePaths in Compile := Seq(),
      herokuSkipSubProjects in Compile := true,
      herokuBuildpacks in Compile := Seq(),
      herokuFatJar in Compile := None
    )
  }

  import com.heroku.sbt.HerokuPlugin.autoImport._

  override def requires = sbt.plugins.JvmPlugin

  override def trigger = allRequirements

  override val projectSettings: Seq[Setting[_]] =
    inConfig(Compile)(baseHerokuSettings) ++
    Seq(
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _ ),
      extraLoggers := {
        val currentFunction = extraLoggers.value
        (key: ScopedKey[_]) => {
          val taskOption = key.scope.task.toOption
          val loggers = currentFunction(key)
          if (taskOption.map(_.label) == Some("deployHeroku") || taskOption.map(_.label) == Some("deployHerokuSlug"))
            new HerokuLogger(target.value / "heroku" / "diagnostics.log") +: loggers
          else
            loggers
        }
      }
    )
}

class HerokuLogger (diagnosticsFile: File) extends BasicLogger {
  IO.writeLines(diagnosticsFile, Seq(
    "+--------------------------------------------------------------------",
    "| JDK Version -> " + System.getProperty("java.version"),
    "| Java Vendor -> " + System.getProperty("java.vendor.url"),
    "| Java Home   -> " + System.getProperty("java.home"),
    "| OS Arch     -> " + System.getProperty("os.arch"),
    "| OS Name     -> " + System.getProperty("os.name"),
    "| JAVA_OPTS   -> " + System.getenv("JAVA_OPTS"),
    "| SBT_OPTS    -> " + System.getenv("SBT_OPTS"),
    "+--------------------------------------------------------------------"
  ))

  def log(level: Level.Value, message: => String): Unit =
    IO.write(diagnosticsFile, message + "\n", IO.defaultCharset, true)

  def trace(t: => Throwable): Unit = {
    IO.write(diagnosticsFile, t.getMessage, IO.defaultCharset, true)
    IO.writeLines(diagnosticsFile, t.getStackTrace.map("    " + _.toString), IO.defaultCharset, true)
  }

  def success(message: => String): Unit =
    IO.write(diagnosticsFile, message + "\n", IO.defaultCharset, true)

  def control(event: ControlEvent.Value, message: => String): Unit = {}
  def logAll(events: Seq[LogEvent]): Unit = events.foreach(log)
}
