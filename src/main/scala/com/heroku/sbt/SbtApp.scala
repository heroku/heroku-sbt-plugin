package com.heroku.sbt

import java.io.File
import java.net.URL

import com.heroku.sdk.deploy.App
import org.apache.http.client.HttpResponseException
import sbt.compiler.CompileFailed
import sbt.{Logger, _}

import scala.collection.JavaConversions

class SbtApp(buildPackDesc:String, name:String, rootDir:File, targetDir:File, log:Logger) extends App(buildPackDesc, name, rootDir, targetDir) {

  sealed trait PackageType
  case class Universal(dir:File) extends PackageType
  case class StartScript(dir:File) extends PackageType

  var percentUpload = 0

  override def logDebug(message:String) {
    log.debug(message)
  }

  override def logInfo(message:String) {
    log.info(message)
  }

  override def logWarn(message:String) {
    log.warn(message)
  }

  override def logUploadProgress(uploaded:java.lang.Long, contentLength:java.lang.Long) {
    val newPercent = Math.round((uploaded / contentLength.toFloat) * 100)
    if (percentUpload != newPercent) {
      percentUpload = newPercent
      log.info("\u001B[A\r\u001B[2K" + s"[${Level.Info.toString}] -----> Uploading slug... ($percentUpload%)")
    }
  }

  override def isUploadProgressEnabled: java.lang.Boolean = {
    ConsoleLogger.formatEnabled && !("false" == System.getProperty("heroku.log.format"))
  }

  override def deploy(includedFiles:java.util.List[java.io.File], configVars:java.util.Map[String,String], jdkVersion:String, jdkUrl:URL, stack:String, processTypes:java.util.Map[String,String], slugFileName:String) {
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

    val defaultProcessTypes =
      if ((getRootDir / "Procfile").exists())
        Map[String,String]()
      else packageType match {
        case Universal(dir) =>
          val startScript = (dir / "bin" ** "*").
            filter(!_.getName.endsWith(".bat")).
            filter(!_.getName.equals("bin")).
            get(0).getName
          if (io.Source.fromFile(targetDir / "/universal/stage/bin" / startScript).mkString.contains("-main")) {
            Map[String, String](
              "web" -> ("target/universal/stage/bin/" + startScript + " -Dhttp.port=$PORT"),
              "console" -> ("target/universal/stage/bin/" + startScript + " -main scala.tools.nsc.MainGenericRunner -usejavacp")
            )
          } else {
            Map[String, String]("web" -> ("target/universal/stage/bin/" + startScript + " -Dhttp.port=$PORT"))
          }
        case StartScript(dir) =>
          Map[String,String]("web" -> "target/start -Dhttp.port=$PORT $JAVA_OPTS")
      }

    // OMG
    val javaProcessTypes = JavaConversions.mapAsJavaMap(defaultProcessTypes ++ JavaConversions.mapAsScalaMap(processTypes))

    try {
      super.deploy(includedFiles, configVars, jdkVersion, jdkUrl, stack, javaProcessTypes, slugFileName)
    } catch {
      case e: HttpResponseException =>
        if (e.getStatusCode == 404) {
          throw new CompileFailed(Array(), s"Could not find app '$name'. Check that herokuAppName setting is correct.", Array())
        } else if (e.getStatusCode == 403 || e.getStatusCode == 401) {
          throw new CompileFailed(Array(), "Check that herokuAppName name is correct. If it is, check that HEROKU_API_KEY is correct or if the Heroku Toolbelt is installed.", Array())
        }
        throw e
    }
  }

  override def prepare(includedFiles:java.util.List[java.io.File], processTypes:java.util.Map[String,String]): Unit = {
    val defaultIncludedFiles = packageType match {
      case Universal(dir) =>
        IO.delete(targetDir / "universal" / "stage" / "bin" / "RUNNING_PID")
        Seq(dir)
      case StartScript(dir) =>
        Seq(dir, targetDir / "staged")
    }

    // OMG
    val javaIncludedFiles = JavaConversions.seqAsJavaList(defaultIncludedFiles ++ JavaConversions.collectionAsScalaIterable(includedFiles))

    super.prepare(javaIncludedFiles, processTypes)

    sbt.IO.copyDirectory(targetDir / "resolution-cache" / "reports", getAppDir / "target" / "resolution-cache" / "reports")

    packageType match {
      case Universal(dir) => IO.delete(getAppDir / "target" / "universal" / "stage" / "bin" / "RUNNING_PID")
      case _ =>
    }
  }

  def packageType: PackageType = {
    if ((targetDir / "universal").exists) {
      Universal(targetDir / "universal" / "stage")
    } else if ((targetDir / "start").exists) {
      StartScript(targetDir / "start")
    } else {
      throw new CompileFailed(Array(), "You must run the `stage` task before deploying your app!", Array())
    }
  }

}
