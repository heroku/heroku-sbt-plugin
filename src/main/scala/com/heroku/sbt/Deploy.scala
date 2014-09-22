package com.heroku.sbt

import java.io.{InputStreamReader, BufferedReader, FileInputStream, BufferedInputStream}
import java.net.{URL, URLEncoder}
import javax.net.ssl.HttpsURLConnection

import sbt._
import sbt.compiler.CompileFailed
import sun.misc.BASE64Encoder
import com.github.pathikrit.dijon._

object Deploy {
  def apply(baseDirectory: java.io.File, targetDir: java.io.File, jdkVersion: String, appName: String, configVars: Map[String,String], procTypes: Map[String,String], includePaths: Seq[String], log: Logger): Unit = {

    // TODO externalize these URLs
    val jdkUrl = Map[String, String](
      "1.6" -> "https://lang-jvm.s3.amazonaws.com/jdk/openjdk1.6-latest.tar.gz",
      "1.7" -> "https://lang-jvm.s3.amazonaws.com/jdk/openjdk1.7-latest.tar.gz",
      "1.8" -> "https://lang-jvm.s3.amazonaws.com/jdk/openjdk1.8-latest.tar.gz"
    )(jdkVersion)

    if (jdkUrl == null) throw new IllegalArgumentException("'" + jdkVersion + "' is not a valid JDK version")

    new java.net.URL(jdkUrl)

    apply(baseDirectory, targetDir, new java.net.URL(jdkUrl), appName, configVars, procTypes, includePaths, log)
  }

  def apply(baseDirectory: java.io.File, targetDir: java.io.File, jdkUrl: URL, appName: String, configVars: Map[String,String], procTypes: Map[String,String], includePaths: Seq[String], log: Logger): Unit = {
    if (appName.isEmpty) throw new IllegalArgumentException("herokuAppName must be defined")

    log.info("---> Packaging application...")
    val herokuDir = targetDir / "heroku"
    val appDir = herokuDir / "app"
    val appTargetDir = appDir / "target"

    val encodedApiKey = getApiKey

    val appData = buildSlug(targetDir, appTargetDir, herokuDir, appDir, jdkUrl)
    appData.getIncludedFiles.foreach { case (originalLocation, _) =>
      log.info("     - including: ./" + sbt.IO.relativize(targetDir.getParentFile, originalLocation).get)
    }

    includePaths.foreach {
      case (path: String) =>
        log.info("     - including: ./" + path)
        val source = targetDir / ".." / path
        if (source.isDirectory) {
          sbt.IO.copyDirectory(source, appDir / path)
        } else {
          sbt.IO.copyFile(source, appDir / path)
        }
    }

    val slugJson = createSlugData(appData.getDefaultProcessTypes ++ procTypes)
    log.debug("Heroku Slug data: " + slugJson)

    try {
      log.info("---> Creating slug...")
      val slugFile = Tar.create("slug", "./app", herokuDir)
      log.info("     - file: ./" + sbt.IO.relativize(targetDir.getParentFile, slugFile).get)
      log.info("     - size: " + (slugFile.length / (1024 * 1024))  + "MB")

      val existingConfigVars = GetConfigVars(appName, encodedApiKey)
      log.debug("Heroku existing config variables: " + existingConfigVars)

      SetConfigVars(appName, encodedApiKey,
        addConfigVar("PATH", ".jdk/bin:/usr/local/bin:/usr/bin:/bin", existingConfigVars) ++
          addConfigVar("JAVA_OPTS", "-Xmx384m -Xss512k -XX:+UseCompressedOops", existingConfigVars) ++
          addConfigVar("SBT_OPTS", "-Xmx384m -Xss512k -XX:+UseCompressedOops", existingConfigVars) ++
          configVars)

      val slugResponse = CreateSlug(appName, encodedApiKey, slugJson)
      log.debug("Heroku Slug response: " + slugResponse.toMap.filter(_._2 != null))

      val blobUrl = slugResponse.blob.url.as[String].get
      val slugId = slugResponse.id.as[String].get
      val stackName = slugResponse.stack.name.as[String].get

      log.debug("Heroku Blob URL: " + blobUrl)
      log.debug("Heroku Slug Id: " + slugId)

      log.info("---> Uploading slug...")
      UploadSlug(blobUrl, slugFile)
      log.info("     - id: " + slugId)
      log.info("     - stack: " + stackName)
      log.info("     - process types: " + slugResponse.process_types.toMap.keys.mkString(", "))

      log.info("---> Releasing...")
      val releaseResponse = ReleaseSlug(appName, encodedApiKey, slugId)
      log.debug("Heroku Release response: " + releaseResponse)
      log.info("     - version: " + releaseResponse.version.as[Double].get.toInt)
    } catch {
      case ce: CurlException =>
        if (ce.getCode == 404) {
          throw new CompileFailed(Array(), "Could not find app '"+appName+"'. Check that herokuAppName setting is correct.", Array())
        } else if (ce.getCode == 403 || ce.getCode == 401) {
          throw new CompileFailed(Array(), "Check that HEROKU_API_KEY is set correctly, or if the Heroku Toolbelt is installed.", Array())
        }
        throw ce
    } finally {
      // move back because we had to move earlier to save file permissions
      appData.getIncludedFiles.foreach { case (originalLocation, newLocation) =>
        log.debug("Heorku moving: " + newLocation.getPath + " -> " + originalLocation.getPath)
        sbt.IO.move(newLocation, originalLocation)
      }
    }
  }

  def buildSlug(targetDir: File, appTargetDir: File, herokuDir: File, appDir: File, jdkUrl: URL): AppData = {
    if ((targetDir / "universal").exists) {
      buildUniversalSlug(targetDir, appTargetDir, herokuDir, appDir, jdkUrl)
    } else if ((targetDir / "start").exists) {
      buildStartScriptSlug(targetDir, appTargetDir, herokuDir, appDir, jdkUrl)
    } else {
      throw new CompileFailed(Array(), "You must stage your application before deploying it!", Array())
    }
  }

  def addConfigVar(key: String, value: String, existingConfigVars: Map[String,String]): Map[String,String] = {
    if (!existingConfigVars.contains(key) || !value.equals(existingConfigVars(key))) {
      Map(key -> value)
    } else {
      Map()
    }
  }

  def buildUniversalSlug(targetDir: File, appTargetDir: File, herokuDir: File, appDir: File, jdkUrl: URL): AppData = {
    val includedFiles = Map[File,File](targetDir / "universal" / "stage" -> appTargetDir / "universal" / "stage")

    // move because copy won't keep file permissions. we'll put it back later
    includedFiles.foreach { case (originalLocation, newLocation) =>
      sbt.IO.move(originalLocation, newLocation)
    }

    installJdk(herokuDir, appDir, jdkUrl)

    val startScript = (appTargetDir / "universal" / "stage" / "bin" ** "*").
      filter(!_.getName.endsWith(".bat")).
      filter(!_.getName.equals("bin")).
      get(0)

    new AppData(includedFiles,
      Map("web" -> ("target/universal/stage/bin/" + startScript.getName + " -Dhttp.port=$PORT")))
  }

  def buildStartScriptSlug(targetDir: File, appTargetDir: File, herokuDir: File, appDir: File, jdkUrl: URL): AppData = {
    val includedFiles = Map[File,File](
      targetDir / "start" -> appTargetDir / "start",
      targetDir / "staged" -> appTargetDir / "staged")

    // move because copy won't keep file permissions. we'll put it back later
    includedFiles.foreach { case (originalLocation, newLocation) =>
      sbt.IO.move(originalLocation, newLocation)
    }

    installJdk(herokuDir, appDir, jdkUrl)

    new AppData(includedFiles,
      Map("web" -> "target/start -Dhttp.port=$PORT $JAVA_OPTS"))
  }

  def createSlugData(procTypes: Map[String,String]): String = {
    "{" +
      "\"buildpack_provided_description\":\"sbt-heroku\"," +
      "\"process_types\":{" + procTypes.foldLeft(""){
      case (s, (k, v)) => (if (s.isEmpty) s else s + ", ") + "\"" + k + "\":\"" + v + "\""
    } + "}}"
  }

  def getApiKey: String = {
    var apiKey = System.getenv("HEROKU_API_KEY")
    if (null == apiKey || apiKey.equals("")) {
      apiKey = sbt.Process("heroku", Seq("auth:token")).!!
    }
    new BASE64Encoder().encode((":" + apiKey).getBytes)
  }

  def installJdk(herokuDir: File, appDir: File, jdkUrl: URL): Unit = {
    val jdkHome = appDir / ".jdk"
    sbt.IO.createDirectory(jdkHome)

    val jdkTgz = herokuDir / "jdk-pkg.tar.gz"
    sbt.IO.download(jdkUrl, jdkTgz)

    Tar.extract(jdkTgz, jdkHome)
  }
}

class AppData (includedFiles: Map[File, File], processTypes: Map[String,String]) {
  def getIncludedFiles=includedFiles
  def getDefaultProcessTypes=processTypes
}

object CreateSlug {
  def apply(appName: String, encodedApiKey: String, data: String): SomeJson = {
    val urlStr = "https://api.heroku.com/apps/" + URLEncoder.encode(appName, "UTF-8") + "/slugs"

    val headers = Map(
      "Authorization" -> encodedApiKey,
      "Content-Type" -> "application/json",
      "Accept" -> "application/vnd.heroku+json; version=3")

    Curl(urlStr, "POST", data, headers)
  }
}

object GetConfigVars {
  def apply(appName: String, encodedApiKey: String): Map[String,String] = {
    val urlStr = "https://api.heroku.com/apps/" + URLEncoder.encode(appName, "UTF-8") + "/config-vars"

    val headers = Map(
      "Authorization" -> encodedApiKey,
      "Accept" -> "application/vnd.heroku+json; version=3")

    Curl(urlStr, "GET", headers).toMap.mapValues[String](_.toString())
  }
}

object SetConfigVars {
  def apply(appName: String, encodedApiKey: String, config: Map[String,String]): Unit = {
    if (config.nonEmpty) {
      val urlStr = "https://api.heroku.com/apps/" + URLEncoder.encode(appName, "UTF-8") + "/config_vars"

      val data = "{" + config.map { case (k, v) => "\"" + k + "\":\"" + v + "\""}.mkString(",") + "}"

      val headers = Map(
        "Authorization" -> encodedApiKey,
        "Content-Type" -> "application/json")

      Curl(urlStr, "PUT", data, headers)
    }
  }
}

object UploadSlug {
  def apply(blobUrl: String, slug: File): Unit = {
    val url = new URL(blobUrl)
    val connection = url.openConnection.asInstanceOf[HttpsURLConnection]

    connection.setDoOutput(true)
    connection.setRequestMethod("PUT")
    connection.setConnectTimeout(0)
    connection.setRequestProperty("Content-Type", "")

    connection.connect()
    val out = connection.getOutputStream
    val in = new BufferedInputStream(new FileInputStream(slug))

    val buffer = Array.ofDim[Byte](1024)
    var length = in.read(buffer)
    while (length != -1) {
      out.write(buffer, 0, length)
      out.flush()
      length = in.read(buffer)
    }
    out.close()
    in.close()
    val responseCode = connection.getResponseCode

    if (responseCode != 200) {
      throw new RuntimeException("Failed to upload slug (HTTP/1.1 " + responseCode + ")")
    }
  }
}

object ReleaseSlug {
  def apply(appName: String, encodedApiKey: String, slugId: String): SomeJson = {
    val urlStr = "https://api.heroku.com/apps/" + appName + "/releases"

    val headers = Map(
      "Authorization" -> encodedApiKey,
      "Content-Type" -> "application/json",
      "Accept" -> "application/vnd.heroku+json; version=3")

    val data = "{\"slug\":\"" + slugId + "\"}"

    Curl(urlStr, "POST", data, headers)
  }
}