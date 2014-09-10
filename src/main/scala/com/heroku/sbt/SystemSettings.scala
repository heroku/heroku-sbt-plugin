package com.heroku.sbt

object SystemSettings {

  def hasNio: Boolean = {
    val ver = System.getProperty("java.specification.version")
    "1.7".equals(ver) || "1.8".equals(ver)
  }
}
