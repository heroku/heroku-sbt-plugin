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
    val herokuFatJar = taskKey[Option[File]]("The path to a Fat JAR to deploy.")

    lazy val baseHerokuSettings: Seq[Def.Setting[_]] = Seq(
      deployHeroku := {
        // TODO this should be able to detect sub-projects in a standard way, and filter sub-projects so that
        // some could be built and some could be skipped
        val baseDirectoryValue = baseDirectory.value
        val herokuSkipSubProjectsValue = (herokuSkipSubProjects in deployHeroku).value
        val jdkVersion = herokuJdkVersion.value
        val buildpacks = JavaConversions.seqAsJavaList(herokuBuildpacks.value)
        val fatjar = herokuFatJar.value
        val targetValue = target.value
        val streamsValue = streams.value
        val streamsLog = streamsValue.log
        val herokuAppNameValue = herokuAppName.value
        val configVars = JavaConversions.mapAsJavaMap(herokuConfigVars.value)
        val processTypes = JavaConversions.mapAsJavaMap(herokuProcessTypes.value)
        val herokuIncludePathsValue = herokuIncludePaths.value
        if ((baseDirectoryValue / "project").exists || !herokuSkipSubProjectsValue) {
          val includedFiles = JavaConversions.seqAsJavaList(herokuIncludePathsValue.map {
            case path: String => new java.io.File(path)
          })
          new SbtApp(
              "sbt-heroku",
              herokuAppNameValue,
              baseDirectoryValue,
              targetValue,
              buildpacks,
              fatjar,
              streamsLog
            ).deploy(includedFiles, configVars, jdkVersion, processTypes, "build.tgz")
        }
      },
      herokuJdkVersion in Compile := "1.8",
      herokuAppName in Compile := System.getProperty("heroku.appName", ""),
      herokuConfigVars in Compile := Map[String,String](),
      herokuProcessTypes in Compile := Map[String,String](),
      herokuIncludePaths in Compile := Seq(),
      herokuSkipSubProjects in Compile := true,
      herokuBuildpacks in Compile := Seq(),
      herokuFatJar in Compile := None
    )
  }

  import com.heroku.sbt.HerokuPlugin.autoImport._

  override def requires = sbt.plugins.JvmPlugin

  override def trigger = allRequirements

  override val projectSettings: Seq[Setting[_]] = {
    val scalaCompiler = if (scalaVersion.value.startsWith("3.")) "scala3-compiler" else "scala-compiler"
    inConfig(Compile)(baseHerokuSettings) ++
    Seq(
      libraryDependencies += "org.scala-lang" % scalaCompiler % scalaVersion.value % "runtime",
      extraLoggers := {
        val currentFunction = extraLoggers.value
        val targetValue = target.value
        (key: ScopedKey[_]) => {
          val taskOption = key.scope.task.toOption
          val loggers = currentFunction(key)
          if (taskOption.map(_.label) == Some("deployHeroku"))
            loggers :+ new HerokuLogger(targetValue / "heroku" / "diagnostics.log")
          else
            loggers
        }
      }
    )
  }
}
