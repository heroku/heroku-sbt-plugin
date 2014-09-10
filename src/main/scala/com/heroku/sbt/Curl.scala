package com.heroku.sbt

import java.io.{InputStreamReader, BufferedReader}
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object Curl {
  def apply(urlStr: String, method: String, headers: Map[String, String]): String = {
    val url = new URL(urlStr)
    val con = url.openConnection.asInstanceOf[HttpsURLConnection]
    con.setDoInput(true)
    con.setRequestMethod(method)

    headers.foreach { case (key, value) => con.setRequestProperty(key, value)}

    val reader = new BufferedReader(new InputStreamReader(con.getInputStream))

    var output = ""
    var tmp = reader.readLine
    while (tmp != null) {
      output += tmp
      tmp = reader.readLine
    }
    output
  }

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
      output += tmp
      tmp = reader.readLine
    }
    output
  }
}