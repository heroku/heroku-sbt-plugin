package com.heroku.sbt

import sbt._

/**
  * @author Joe Kutner on 8/15/17.
  *         Twitter: @codefinger
  */
class HerokuLogger (diagnosticsFile: File) extends BasicLogger {
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

  def log(level: Level.Value, message: => String): Unit =
    IO.write(diagnosticsFile, message + "\n", IO.defaultCharset, true)

  def trace(t: => Throwable): Unit = {
    IO.write(diagnosticsFile, t.getMessage, IO.defaultCharset, true)
    IO.writeLines(diagnosticsFile, t.getStackTrace.map("    " + _.toString), IO.defaultCharset, true)
  }

  def success(message: => String): Unit =
    IO.write(diagnosticsFile, message + "\n", IO.defaultCharset, true)

  def control(event: ControlEvent.Value, message: => String): Unit = {}
  def logAll(events: Seq[LogEvent]): Unit = events.foreach(log)
}