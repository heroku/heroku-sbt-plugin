Heroku sbt Plugin
=================
[![](https://badgen.net/github/license/heroku/sbt-heroku)](LICENSE)
[![CI](https://github.com/heroku/sbt-heroku/actions/workflows/ci.yml/badge.svg)](https://github.com/heroku/sbt-heroku/actions/workflows/ci.yml)

> [!IMPORTANT]
> This plugin is no longer maintained.
>
> Heroku offers a build tool independent CLI for the purpose of deploying JAR and WAR files directly to Heroku without
> using the standard Git workflow. Users that require the functionality of the Heroku sbt Plugin are encouraged to
> migrate to it.
>
> Find out more: [Heroku JVM Application Deployer](https://github.com/heroku/heroku-jvm-application-deployer)

This plugin is used to deploy Scala and Play applications directly to Heroku without pushing to a Git repository.
This is can be useful when deploying from a CI server.

## Using the Plugin

Add the following to your `project/plugins.sbt` file:

```scala
addSbtPlugin("com.heroku" % "sbt-heroku" % "2.1.4")
```

If you're not using Play, then you'll also need to add the
[sbt-native-packager plugin](https://github.com/sbt/sbt-native-packager), which creates a `stage` task.
Alternatively, you can [deploy a fat JAR using sbt-assembly](#deploying-a-fat-jar).

Next, add something like this to your `build.sbt` if you do not have a Heroku Git repo in your `git remote`s.

```scala
herokuAppName in Compile := "your-heroku-app-name"
```

Now, if you have the [Heroku CLI](https://devcenter.heroku.com/articles/heroku-cli) installed, run:

```sh-session
$ sbt stage deployHeroku
```

If you do not have the CLI installed, then run:

```sh-session
$ HEROKU_API_KEY="xxx-xxx-xxxx" sbt stage deployHeroku
```

And replace "xxx-xxx-xxxx" with the value of your Heroku API token.

### Requirements

+  It is required that you use sbt 0.13.5 or greater.

+  You must use Java 1.7 or higher locally.

+  This plugin has not been tested with Play 2.0 or 2.1.

### Configuring the Plugin

You may set the desired JDK version like so:

```scala
herokuJdkVersion in Compile := "11"
```

For a list of supported JDK versions, refer to the [Heroku Java Support DevCenter article](https://devcenter.heroku.com/articles/java-support#supported-java-versions).

You can (but probably should not) set configuration variables like so:

```scala
herokuConfigVars in Compile := Map(
  "MY_VAR" -> "some value",
  "JAVA_OPTS" -> "-XX:+UseCompressedOops"
)
```

If you adhere to the principles of the [12 Factor app](http://12factor.net/config),
Configuration should be strictly seperated from code. Thus, you do not want to tie your
configuration to your codebase. There are a few exceptions to this, such as `conf/routes`,
and some `JAVA_OPTS` may be universal. But please use `herokuConfigVars` sparingly.

Any variable defined in `herokuConfigVars` will override defaults. However, if you remove
a variable from this list, it will not automatically be removed from your Heroku app
(even on the next deploy).

You may set process types (similar to a `Procfile`) with `herokuProcessTypes`:

```scala
herokuProcessTypes in Compile := Map(
  "web" -> "target/universal/stage/bin/my-app -Dhttp.port=$PORT",
  "worker" -> "java -jar target/universal/stage/lib/my-worker.jar"
)
```

You can include additional directories in the slug (they must be relative to the project root):

```scala
herokuIncludePaths in Compile := Seq(
  "app", "conf/routes", "public/javascripts"
)
```

You can run the plugin against all sub-projects (in addition to the root project) by setting the following option:

```scala
herokuSkipSubProjects in Compile := false
```

This defaults to `true` (and currently it only runs against all sub-projects or none).

You can disable the upload progress in the console by setting the `heroku.log.format` system property to `false`, like this:

```
$ sbt -Dheroku.log.format=false deployHeroku
```

See the `src/sbt-test` directory for examples.

## Deploying a Fat JAR

If you are packaging your application with [sbt-assembly](https://github.com/sbt/sbt-assembly) or any other plugin that
produces a "fat JAR", you can deploy that file by adding the following configuration to your `build.sbt`:

```scala
herokuFatJar in Compile := Some((assemblyOutputPath in assembly).value)
```

If not using sbt-assembly, you may replace `(assemblyOutputPath in assembly).value` with the relative path to your JAR file.
Then you can deploy by running:

```
$ sbt assembly deployHeroku
```

The sbt-heroku plugin will skip the sbt-native-packager bits and deploy your JAR directly to Heroku.

## Running a Remote Console

When using `sbt-native-packager` version 0.7.6 or greater, sbt-heroku will create a
`console` process type for you. This command can be run like so:

```sh-session
$ heroku run console -a <appname>
Running `console` attached to terminal... up, run.5154
Picked up JAVA_TOOL_OPTIONS: -Xmx384m  -Djava.rmi.server.useCodebaseOnly=true
Failed to created JLineReader: java.lang.NoClassDefFoundError: scala/tools/jline/console/completer/Completer
Falling back to SimpleReader.
Welcome to Scala version 2.10.4 (OpenJDK 64-Bit Server VM, Java 1.8.0_20).
Type in expressions to have them evaluated.
Type :help for more information.

scala>
```

For older versions of Play 2.x it might be necessary to upgrade `sbt-native-packager` manually.
You can do this by adding the following line of code to your `project/plugins.sbt`:

```
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.7.6")
```

Additionally, the is [a bug in Scala 2.11.6](https://issues.scala-lang.org/browse/SI-9199)
that causes this console task to fail. Upgrading to Scala 2.11.7 fixes the issue.

## Deploying to Multiple Environments

To deploy to multiple Heroku app environments, you can use either system properties, environment variables, or any other native sbt/Java configuration method.  For example, you might define your `appName` as a Map and choose a value with
the system property as a key.

```scala
herokuAppName in Compile := Map(
  "test" -> "your-heroku-app-test",
  "stg"  -> "your-heroku-app-stage",
  "prod" -> "your-heroku-app-prod"
).getOrElse(sys.props("appEnv"), "your-heroku-app-dev")
```

Then run the sbt command like so:

```sh-session
$ sbt -DappEnv=test stage deployHeroku
```

### Deploying from Git Branches

Another option when using multiple environments is to deploy from a Git branch that corresponds to the environment. This is particularly useful if you are using [git-flow](https://github.com/nvie/gitflow) or a similar process.

First, add the sbt-git plugin ot your `project/plugins.sbt` like this:

```scala
resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.6.4")
```

Then in your `build.sbt` you can configure the sbt-heroku plugin to deploy to the environment that corresponds to the current Git branch like this:

```scala
import com.typesafe.sbt.SbtGit._

// ...
herokuAppName in Compile := Map(
  "testing"    -> "myapp-testing",
  "staging"    -> "myapp-staging",
  "production" -> "myapp"
).getOrElse(git.gitCurrentBranch.value, "myapp-dev")

showCurrentGitBranch
```

## Hacking

In order to run the test suite, you must have the [Heroku CLI](https://devcenter.heroku.com/articles/heroku-cli) installed. Then run:

```sh-session
$ sbt scripted
```

To run an individual test, use a command like this:

```sh-session
$ sbt "scripted settings/config_vars"
```

The heavy lifting for this plugin is done by the `heroku-deploy` library. The source code for that project can be found
in the [heroku-maven-plugin repository](https://github.com/heroku/heroku-maven-plugin/tree/main/heroku-deploy). If you
need to update that library, do this:

```sh-session
$ git clone https://github.com/heroku/heroku-maven-plugin
$ cd heroku-maven-plugin/heroku-deploy
# make your changes
$ mvn clean install
```

Then update the `heroku-deploy` dependency version in the sbt-heroku `build.sbt` to whatever
version is specified in the heroku-deploy `pom.xml`. The next time you run the `scripted` tests it will pick up the
snapshot version from your local Maven repository.
