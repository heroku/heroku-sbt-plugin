package com.heroku.sbt

import sbt._
import Keys._

import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.compressors.CompressorStreamFactory
import scala.util.Try
import scala.util.Success
import scala.util.Failure

import spray.json._
import DefaultJsonProtocol._

import java.io._
import java.net.URLEncoder
import java.net.URL

import javax.net.ssl.HttpsURLConnection

import sun.misc.BASE64Encoder

object HerokuPlugin extends AutoPlugin {

  object autoImport {

    val deployHeroku = taskKey[String]("Deploy to Heroku.")
    val herokuJdkVersion = settingKey[String]("Set the major version of the JDK to use.")
    val herokuAppName = settingKey[String]("Set the name of the Heroku application.")

    lazy val baseHerokuSettings: Seq[Def.Setting[_]] = Seq(
      deployHeroku := {
        Deploy(target.value, (herokuJdkVersion in deployHeroku).value, (herokuAppName in deployHeroku).value)
      },
      herokuJdkVersion in Compile := "1.7",
      herokuAppName in Compile := ""
    )
  }

  import autoImport._

  override def requires = sbt.plugins.JvmPlugin

  override def trigger = allRequirements

  override val projectSettings =
    inConfig(Compile)(baseHerokuSettings)
}

object Deploy {
  def apply(targetDir: java.io.File, jdkVersion: String, appName: String): String = {
    if (appName.isEmpty) throw new IllegalArgumentException("herokuAppName must be defined")

    val jdkUrl = Map[String, String](
      "1.6" -> "http://heroku-jdk.s3.amazonaws.com/openjdk1.7.0_45.tar.gz",
      "1.7" -> "http://heroku-jdk.s3.amazonaws.com/openjdk1.7.0_45.tar.gz",
      "1.8" -> "http://heroku-jdk.s3.amazonaws.com/openjdk1.7.0_45.tar.gz"
    )(jdkVersion)

    if (jdkUrl == null) throw new IllegalArgumentException("'" + jdkVersion + "' is not a valid JDK version")

    println("---> Packaging application...")
    val herokuDir = targetDir / "heroku"
    val appDir = herokuDir / "app"

    // move because copy won't keep file permissions. we'll put it back later
    sbt.IO.move((targetDir / "universal"), appDir)

    val jdkHome = appDir / ".jdk"
    sbt.IO.createDirectory(jdkHome)

    val jdkTgz = herokuDir / "jdk-pkg.tar.gz"
    sbt.IO.download(new java.net.URL(jdkUrl), jdkTgz)

    sbt.Process("tar", Seq("pxf", jdkTgz.getAbsolutePath, "-C", jdkHome.getAbsolutePath)) !!

    sbt.Process(Seq("tar", "pczf", "slug.tgz", "./app"), herokuDir) !!

    val apiKey = System.getenv("HEROKU_API_KEY")
    val encodedApiKey = new BASE64Encoder().encode((":" + apiKey).getBytes)

    val startScript = (appDir / "stage" / "bin" ** "*").
      filter(!_.getName.endsWith(".bat")).
      filter(!_.getName.equals("bin")).
      get(0)

    val slugData = "{\"process_types\":{\"web\":\"stage/bin/" + startScript.getName + "\"}}"

    println("---> Creating Slug...")
    val slugResponse = CreateSlug(appName, encodedApiKey, slugData)

    val slugJson = slugResponse.parseJson.asJsObject
    var blobUrl = slugJson.getFields("blob")(0).asJsObject.getFields("url")(0).toString
    var slugId = slugJson.getFields("id")(0).toString
    blobUrl = blobUrl.substring(1, blobUrl.size - 1)
    slugId = slugId.substring(1, slugId.size - 1)

    println("---> Uploading Slug...")
    UploadSlug(blobUrl, herokuDir / "slug.tgz")

    println("---> Releasing the Slug...")
    val releaseResponse = ReleaseSlug(appName, encodedApiKey, slugId)
    println(releaseResponse.parseJson.asJsObject.prettyPrint)

    // clean up
    sbt.IO.move(appDir / "stage", targetDir / "universal" / "stage")
    "success"
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

object Curl {
  def apply(urlStr: String, method: String, data: String, headers: Map[String, String]): String = {
    val url = new URL(urlStr)
    val con = url.openConnection.asInstanceOf[HttpsURLConnection]
    con.setDoInput(true)
    con.setDoOutput(true)
    con.setRequestMethod(method)

    headers.foreach { case (key, value) => con.setRequestProperty(key, value)}

    con.getOutputStream.write(data.getBytes("UTF-8"))

    val reader = new BufferedReader(new InputStreamReader(con.getInputStream))

    var output = ""
    var tmp = reader.readLine
    while (tmp != null) {
      output += tmp; tmp = reader.readLine
    }
    output
  }
}


