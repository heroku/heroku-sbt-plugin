package com.heroku.sbt

import java.io.File
import java.net.URL

import com.heroku.sdk.deploy.{App, Curl}
import sbt.{Logger, _}
import sbt.compiler.CompileFailed

import scala.collection.JavaConversions

class SbtApp(buildPackDesc:String, name:String, rootDir:File, targetDir:File, log:Logger) extends App(buildPackDesc, name, rootDir, targetDir) {

  sealed trait PackageType
  case class Universal(dir:File) extends PackageType
  case class StartScript(dir:File) extends PackageType

  override def logDebug(message:String) {
    log.debug(message)
  }

  override def logInfo(message:String) {
    log.info(message)
  }

  override def logWarn(message:String) {
    log.warn(message)
  }

  override def deploy(includedFiles:java.util.List[java.io.File], configVars:java.util.Map[String,String], jdkVersion:String, jdkUrl:URL, stack:String, processTypes:java.util.Map[String,String]) {
    logDebug(
      s"+--------------------+\n" +
        s"| sbt-heroku details |\n" +
        s"+--------------------+-----------------------------------------------\n" +
        s"| baseDirectory -> $getRootDir \n" +
        s"| targetDir     -> $targetDir \n" +
        s"| jdkVersion    -> $jdkVersion \n" +
        s"| jdkUrl        -> $jdkUrl \n" +
        s"| appName       -> $name \n" +
        s"| stack         -> $stack \n" +
        s"| includePaths  -> " + JavaConversions.collectionAsScalaIterable(includedFiles).mkString(";") + "\n" +
        s"+--------------------------------------------------------------------\n"
    )

    val defaultProcessTypes = packageType match {
      case Universal(dir) =>
        val startScript = (dir / "bin" ** "*").
          filter(!_.getName.endsWith(".bat")).
          filter(!_.getName.equals("bin")).
          get(0).getName
        Map[String,String]("web" -> ("target/universal/stage/bin/" + startScript + " -Dhttp.port=$PORT"))
      case StartScript(dir) =>
        Map[String,String]("web" -> "target/start -Dhttp.port=$PORT $JAVA_OPTS")
    }

    // OMG
    val javaProcessTypes = JavaConversions.mapAsJavaMap(defaultProcessTypes ++ JavaConversions.mapAsScalaMap(processTypes))

    try {
      super.deploy(includedFiles, configVars, jdkVersion, jdkUrl, stack, javaProcessTypes)
    } catch {
      case ce: Curl.CurlException =>
        if (ce.getCode == 404) {
          throw new CompileFailed(Array(), s"Could not find app '$name'. Check that herokuAppName setting is correct.", Array())
        } else if (ce.getCode == 403 || ce.getCode == 401) {
          throw new CompileFailed(Array(), "Check that HEROKU_API_KEY is set correctly, or if the Heroku Toolbelt is installed.", Array())
        }
        throw ce
    }
  }

  override def prepare(includedFiles:java.util.List[java.io.File]): Unit = {
    val defaultIncludedFiles = packageType match {
      case Universal(dir) =>
        Seq(dir)
      case StartScript(dir) =>
        Seq(dir, targetDir / "staged")
    }

    // OMG
    val javaIncludedFiles = JavaConversions.seqAsJavaList(defaultIncludedFiles ++ JavaConversions.collectionAsScalaIterable(includedFiles))

    super.prepare(javaIncludedFiles)

    sbt.IO.copyDirectory(targetDir / "resolution-cache" / "reports", getAppDir / "target" / "resolution-cache" / "reports")
  }

  def packageType: PackageType = {
    if ((targetDir / "universal").exists) {
      Universal(targetDir / "universal" / "stage")
    } else if ((targetDir / "start").exists) {
      StartScript(targetDir / "start")
    } else {
      throw new CompileFailed(Array(), "You must stage your application before deploying it!", Array())
    }
  }

}
