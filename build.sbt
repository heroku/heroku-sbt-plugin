lazy val `sbt-heroku` = project in file(".")

name := "sbt-heroku"

organization := "com.heroku"

sbtPlugin := true

crossSbtVersions := Vector("0.13.16", "1.0.0")

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalacOptions += "-deprecation"

resolvers += Resolver.bintrayRepo("heroku", "maven")

libraryDependencies ++= Seq(
  "com.heroku.sdk" % "heroku-deploy" % "2.0.1"
)

publishMavenStyle := false

// Scripted
scriptedSettings
scriptedLaunchOpts += { "-Dproject.version="+version.value }
scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-XX:MaxPermSize=256M",
    "-Dheroku.uuid=" + java.util.UUID.randomUUID.toString.substring(0,15))
}

// Bintray
bintrayOrganization := Some("heroku")
bintrayRepository := "sbt-plugins"
bintrayPackage := "sbt-heroku"
bintrayReleaseOnPublish := false

// Git
val tagName = Def.setting{
  s"v${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}"
}
val tagOrHash = Def.setting{
  if(isSnapshot.value)
    sys.process.Process("git rev-parse HEAD").lines_!.head
  else
    tagName.value
}

releaseTagName := tagName.value

// Release
import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  // releaseStepCommandAndRemaining("^ test"),
  // releaseStepCommandAndRemaining("^ scripted"),
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("^ publishSigned"),
  releaseStepTask(bintrayRelease in `sbt-heroku`),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
