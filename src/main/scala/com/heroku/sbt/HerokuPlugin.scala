package com.heroku.sbt

import sbt.Keys._
import sbt._

object HerokuPlugin extends AutoPlugin {

  object autoImport {

    val deployHeroku = taskKey[Unit]("Deploy to Heroku.")
    val herokuJdkVersion = settingKey[String]("Set the major version of the JDK to use.")
    val herokuAppName = settingKey[String]("Set the name of the Heroku application.")
    val herokuConfigVars = settingKey[Map[String,String]]("Config variables to set on the Heroku application.")
    val herokuJdkUrl = settingKey[String]("The location of the JDK binary.")
    val herokuProcessTypes = settingKey[Map[String,String]]("The process types to run on Heroku (similar to Procfile).")

    lazy val baseHerokuSettings: Seq[Def.Setting[_]] = Seq(
      deployHeroku := {
        if ((herokuJdkUrl in deployHeroku).value.isEmpty) {
          Deploy(
            target.value,
            (herokuJdkVersion in deployHeroku).value,
            (herokuAppName in deployHeroku).value,
            (herokuConfigVars in deployHeroku).value,
            (herokuProcessTypes in deployHeroku).value,
            streams.value.log)
        } else {
          Deploy(
            target.value,
            new java.net.URL((herokuJdkUrl in deployHeroku).value),
            (herokuAppName in deployHeroku).value,
            (herokuConfigVars in deployHeroku).value,
            (herokuProcessTypes in deployHeroku).value,
            streams.value.log)
        }
      },
      herokuJdkVersion in Compile := "1.7",
      herokuAppName in Compile := "",
      herokuConfigVars in Compile := Map[String,String](),
      herokuProcessTypes in Compile := Map[String,String](),
      herokuJdkUrl in Compile := ""
    )
  }

  import com.heroku.sbt.HerokuPlugin.autoImport._

  override def requires = sbt.plugins.JvmPlugin

  override def trigger = allRequirements

  override val projectSettings =
    inConfig(Compile)(baseHerokuSettings)
}
