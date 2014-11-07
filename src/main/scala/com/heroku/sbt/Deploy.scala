package com.heroku.sbt

import sbt._

class DeployBuilder {
  private var baseDirectory: Option[File] = None
  private var targetDir: Option[File] = None
  private var jdkVersion: Option[String] = None
  private var jdkUrl: Option[URL] = None
  private var appName: Option[String] = None
  private var configVars = Map[String,String]()
  private var procTypes = Map[String,String]()
  private var includePaths = Seq[String]()
  private var logger: Option[Logger] = None

  def withBaseDirectory(dir: File): DeployBuilder = { this.baseDirectory = Some(dir); this }
  def withTargetDirectory(dir: File): DeployBuilder = { this.targetDir = Some(dir); this }
  def withJdkUrl(url: URL): DeployBuilder = { this.jdkUrl = Some(url); this }
  def withAppName(name: String): DeployBuilder = { this.appName = Some(name); this }
  def withConfigVars(vars: Map[String,String]): DeployBuilder = { this.configVars = vars; this }
  def withProcTypes(types: Map[String,String]): DeployBuilder = { this.procTypes = types; this }
  def withIncludePaths(paths: Seq[String]): DeployBuilder = { this.includePaths = paths; this }
  def withLogger(log: Logger): DeployBuilder = { this.logger = Some(log); this }

  def deploy(): Unit = {


    logger.get.debug(
      s"+--------------------+\n" +
      s"| sbt-heroku details |\n" +
      s"+--------------------+-----------------------------------------------\n" +
      s"| baseDirectory -> $baseDirectory \n" +
      s"| targetDir     -> $targetDir \n" +
      s"| jdkUrl        -> $jdkUrl \n" +
      s"| appName       -> $appName \n" +
      s"| configVars    -> " + configVars.mkString(";") + "\n" +
      s"| procTypes     -> " + procTypes.mkString(";") + "\n" +
      s"| includePaths  -> " + includePaths.mkString(";") + "\n" +
      s"+--------------------------------------------------------------------\n"
    )

    val includedFiles = includePaths.map(new java.io.File(_)).toList

    val app = new SbtApp("sbt-heroku", appName.get, baseDirectory.get, targetDir.get, logger.get)

    if (jdkUrl.isEmpty) {
      app.deploy(null, null, jdkVersion.get, null)
    } else {
      app.deploy(null, null, jdkUrl.get, null)
    }
  }

}