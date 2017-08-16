package com.heroku.sbt

import sbt._
import org.apache.logging.log4j.core.layout.Rfc5424Layout
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.LogEvent

/**
  * @author Joe Kutner on 8/15/17.
  *         Twitter: @codefinger
  */
class HerokuLogger(diagnosticsFile:File) extends AbstractAppender("heroku-logger", null, null) {

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

  override def append(event:LogEvent): Unit = {
    // do nothing
  }
}