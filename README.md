Heroku sbt Plugin [![Build Status](https://travis-ci.org/heroku/sbt-heroku.svg?branch=master)](https://travis-ci.org/heroku/sbt-heroku)
=================

This plugin is used to deploy Scala and Play applications directly to Heroku without pushing to a Git repository.
This is can be useful when deploying from a CI server.

## Using the Plugin

Add the following to your `project/plugins.sbt` file:

```
resolvers += Resolver.url("heroku-sbt-plugin-releases",
  url("http://dl.bintray.com/heroku/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.heroku" % "sbt-heroku" % "0.1.3")
```

If you're not using Play, then you'll also need to add the
[sbt-native-packager plugin](https://github.com/sbt/sbt-native-packager).

Next, add something like this to your `build.sbt`

```
herokuAppName in Compile := "your-heroku-app-name"
```

Now, if you have the [Heroku Toolbelt](https://toolbelt.heroku.com/) installed, run:

```
$ sbt stage deployHeroku
```

If you do not have the toolbelt installed, then run:

```
$ HEROKU_API_KEY="xxx-xxx-xxxx" sbt stage deployHeroku
```

And replace "xxx-xxx-xxxx" with the value of your Heroku API token.

### Requirements

+  It is required that you use sbt 0.13.5 or greater.

+  If using Java 1.6 you must have a `tar` command available on your system.

+  This plugin has not been tested with Play 2.0 or 2.1.

### Configuring the Plugin

You may set the desired JDK version like so:

```
herokuJdkVersion in Compile := "1.7"
```

Valid values are `1.6`, `1.7`, and `1.8`. The default is `1.7`

You can set configuration variables like so:

```
herokuConfigVars in Compile := Map(
  "MY_VAR" -> "some value",
  "JAVA_OPTS" -> "-Xmx384m -Xss512k -XX:+UseCompressedOops"
)
```

Any variable defined in `herokuConfigVars` will override defaults.

You may set process types (similar to a `Procfile`) with `herokuProcessTypes`:

```
herokuProcessTypes in Compile := Map(
  "web" -> "target/universal/stage/bin/my-app -Dhttp.port=$PORT",
  "worker" -> "java -jar target/universal/stage/lib/my-worker.jar"
)
```

And you can include additional directories in the slug (they must be relative to the project root):

```
herokuIncludePaths in Compile := Seq(
  "app", "conf/routes", "public/javascripts"
)
```

See the `src/sbt-test` directory for examples.

## Deploying to Multiple Environments

To deploy to multiple Heroku app environments, you can use either system properties, environment variables, or any other
native sbt/Java configuration method.  For example, you might define your `appName` as a Map and choose a value with
the system property as a key.

```
herokuAppName in Compile := Map(
  "test" -> "your-heroku-app-test",
  "stg"  -> "your-heroku-app-stage",
  "prod" -> "your-heroku-app-prod"
).getOrElse(sys.props("appEnv"), "your-heroku-app-dev")
```

Then run the sbt command like so:

```
$ sbt -DappEnv=test stage deployHeroku
```

## Hacking

In order to run the test suite, you must have the [Heroku Toolbelt](https://toolbelt.heroku.com/) installed. Then run:

```
$ sbt scripted
```
