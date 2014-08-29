addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.6.2")

libraryDependencies <+= (sbtVersion) { sv =>
  "org.scala-sbt" % "scripted-plugin" % sv
}