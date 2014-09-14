package com.heroku.sbt

import java.io.{InputStream, FileNotFoundException, InputStreamReader, BufferedReader}
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import com.github.pathikrit.dijon._

object Curl {
  def apply(urlStr: String, method: String, headers: Map[String, String]): SomeJson = {
    val url = new URL(urlStr)
    val con = url.openConnection.asInstanceOf[HttpsURLConnection]
    con.setDoInput(true)
    con.setRequestMethod(method)

    headers.foreach { case (key, value) => con.setRequestProperty(key, value)}

    handleResponse(con)
  }

  def apply(urlStr: String, method: String, data: String, headers: Map[String, String]): SomeJson = {
    val url = new URL(urlStr)
    val con = url.openConnection.asInstanceOf[HttpsURLConnection]
    con.setDoInput(true)
    con.setDoOutput(true)
    con.setRequestMethod(method)

    headers.foreach { case (key, value) => con.setRequestProperty(key, value)}

    con.getOutputStream.write(data.getBytes("UTF-8"))

    handleResponse(con)
  }

  def handleResponse(con: HttpsURLConnection): SomeJson = {
    try {
      val output = readStream(con.getInputStream)
      parse(output)
    } catch {
      case e: Exception =>
        val output = readStream(con.getErrorStream)
        throw new CurlException(con.getResponseCode, e)
    }
  }

  def readStream(is: InputStream): String = {
    val reader = new BufferedReader(new InputStreamReader(is))
    var output = ""
    var tmp = reader.readLine
    while (tmp != null) {
      output += tmp
      tmp = reader.readLine
    }
    output
  }
}

class CurlException (code:Int, cause:Throwable=null)
  extends java.lang.Exception ("There was an exception invoking the remote service.", cause) {
  def getCode:Int=code
}