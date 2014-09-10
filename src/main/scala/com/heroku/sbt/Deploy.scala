package com.heroku.sbt

import java.io.{InputStreamReader, BufferedReader, FileInputStream, BufferedInputStream}
import java.net.{URL, URLEncoder}
import javax.net.ssl.HttpsURLConnection

import sbt._
import sun.misc.BASE64Encoder

object Deploy {
  def apply(targetDir: java.io.File, jdkVersion: String, appName: String, configVars: Map[String,String], procTypes: Map[String,String], log: Logger): Unit = {

    // TODO externalize these URLs
    val jdkUrl = Map[String, String](
      "1.6" -> "https://lang-jvm.s3.amazonaws.com/jdk/openjdk1.6-latest.tar.gz",
      "1.7" -> "https://lang-jvm.s3.amazonaws.com/jdk/openjdk1.7-latest.tar.gz",
      "1.8" -> "https://lang-jvm.s3.amazonaws.com/jdk/openjdk1.8-latest.tar.gz"
    )(jdkVersion)

    if (jdkUrl == null) throw new IllegalArgumentException("'" + jdkVersion + "' is not a valid JDK version")

    new java.net.URL(jdkUrl)

    apply(targetDir, new java.net.URL(jdkUrl), appName, configVars, procTypes, log)
  }

  def apply(targetDir: java.io.File, jdkUrl: URL, appName: String, configVars: Map[String,String], procTypes: Map[String,String], log: Logger): Unit = {
    if (appName.isEmpty) throw new IllegalArgumentException("herokuAppName must be defined")

    log.info("---> Packaging application...")
    val herokuDir = targetDir / "heroku"
    val appDir = herokuDir / "app"
    val appTargetDir = appDir / "target"

    val encodedApiKey = getApiKey

    val slugData = createSlugData(buildSlug(targetDir, appTargetDir, herokuDir, appDir, jdkUrl, procTypes, log))

    val slugFile = Tar.create("slug", "./app", herokuDir)

    log.info("---> Creating Slug...")
    SetConfigVars(appName, encodedApiKey, Map(
      "PATH" -> ".jdk/bin:/usr/local/bin:/usr/bin:/bin",
      "JAVA_OPTS" -> "-Xmx384m -Xss512k -XX:+UseCompressedOops",
      "SBT_OPTS" -> "-Xmx384m -Xss512k -XX:+UseCompressedOops"
    ) ++ configVars)
    val slugResponse = CreateSlug(appName, encodedApiKey, slugData)

    log.debug("Heroku Slug response: " + slugResponse)

    val blobUrl = parseSlugResponseForBlobUrl(slugResponse)
    val slugId = parseSlugResponseForSlugId(slugResponse)

    log.debug("Heroku Blob URL: " + blobUrl)
    log.debug("Heroku Slug Id: " + slugId)

    log.info("---> Uploading Slug...")
    UploadSlug(blobUrl, slugFile)

    log.info("---> Releasing the Slug...")
    val releaseResponse = ReleaseSlug(appName, encodedApiKey, slugId)
    log.debug("Heroku Release response: " + releaseResponse)
  }

  def buildSlug(targetDir: File, appTargetDir: File, herokuDir: File, appDir: File, jdkUrl: URL, procTypes: Map[String,String], log: Logger): Map[String,String] = {
    if ((targetDir / "universal").exists) {
      buildUniversalSlug(targetDir, appTargetDir, herokuDir, appDir, jdkUrl, procTypes)
    } else if ((targetDir / "start").exists) {
      buildStartScriptSlug(targetDir, appTargetDir, herokuDir, appDir, jdkUrl, procTypes)
    } else {
      log.error("You must stage your application before deploying it!")
      throw new IllegalArgumentException()
    }
  }

  def buildUniversalSlug(targetDir: File, appTargetDir: File, herokuDir: File, appDir: File, jdkUrl: URL, procTypes: Map[String,String]): Map[String,String] = {
    // move because copy won't keep file permissions. we'll put it back later
    sbt.IO.move(targetDir / "universal", appTargetDir / "universal")

    installJdk(herokuDir, appDir, jdkUrl)

    if (procTypes.isEmpty) {
      val startScript = (appTargetDir / "universal" / "stage" / "bin" ** "*").
        filter(!_.getName.endsWith(".bat")).
        filter(!_.getName.equals("bin")).
        get(0)

      Map("web" -> ("target/universal/stage/bin/" + startScript.getName + " -Dhttp.port=$PORT"))
    } else {
      procTypes
    }
  }

  def buildStartScriptSlug(targetDir: File, appTargetDir: File, herokuDir: File, appDir: File, jdkUrl: URL, procTypes: Map[String,String]): Map[String,String] = {
    // move because copy won't keep file permissions. we'll put it back later
    sbt.IO.move(targetDir / "start", appTargetDir / "start")
    sbt.IO.move(targetDir / "staged", appTargetDir / "staged")

    installJdk(herokuDir, appDir, jdkUrl)

    if (procTypes.isEmpty) {
      Map("web" -> "target/start -Dhttp.port=$PORT $JAVA_OPTS")
    } else {
      procTypes
    }
  }

  def createSlugData(procTypes: Map[String,String]): String = {
    "{\"process_types\":{" + procTypes.foldLeft(""){
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

  def parseSlugResponseForBlobUrl(json: String): String = {
    val urlMatch = """(?<="url"\s?:\s?")https://\S+(?="\s?\n?\r?}\s?,)""".r.findFirstIn(json)
    if (urlMatch.isEmpty) throw new Exception("No blob url returned by Platform API!")
    urlMatch.head
  }

  def parseSlugResponseForSlugId(json: String): String = {
    val urlMatch = """(?<="id"\s?:\s?")\S+(?="\s?\n?\r?,)""".r.findFirstIn(json)
    if (urlMatch.isEmpty) throw new Exception("No blob url returned by Platform API!")
    urlMatch.head
  }
}

object CreateSlug {
  def apply(appName: String, encodedApiKey: String, data: String): String = {
    val urlStr = "https://api.heroku.com/apps/" + URLEncoder.encode(appName, "UTF-8") + "/slugs"

    val headers = Map(
      "Authorization" -> encodedApiKey,
      "Content-Type" -> "application/json",
      "Accept" -> "application/vnd.heroku+json; version=3")

    Curl(urlStr, "POST", data, headers)
  }
}

object SetConfigVars {
  def apply(appName: String, encodedApiKey: String, config: Map[String,String]): String = {
    val urlStr = "https://api.heroku.com/apps/" + URLEncoder.encode(appName, "UTF-8") + "/config_vars"

    val data = "{" + config.map{case(k,v) => "\""+k+"\":\""+v+"\""}.mkString(",") + "}"

    val headers = Map(
      "Authorization" -> encodedApiKey,
      "Content-Type" -> "application/json")

    Curl(urlStr, "PUT", data, headers)
  }
}

object UploadSlug {
  def apply(blobUrl: String, slug: File): Int = {
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
    connection.getResponseCode
  }
}

object ReleaseSlug {
  def apply(appName: String, encodedApiKey: String, slugId: String): String = {
    val urlStr = "https://api.heroku.com/apps/" + appName + "/releases"

    val headers = Map(
      "Authorization" -> encodedApiKey,
      "Content-Type" -> "application/json",
      "Accept" -> "application/vnd.heroku+json; version=3")

    val data = "{\"slug\":\"" + slugId + "\"}"

    Curl(urlStr, "POST", data, headers)
  }
}