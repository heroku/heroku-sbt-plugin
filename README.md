Heroku sbt Plugin
=================

This plugin is used to deploy Scala and Play applications directly to Heroku without pushing to a Git repository.
This is can be useful when deploying from a CI server.

## Using the Plugin

Add the following to your `project/plugins.sbt` file:

```
addSbtPlugin("com.heroku" % "sbt-heroku" % "0.1-SNAPSHOT")
```

Then add something like this to your `build.sbt`

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

### Requirements

It is required that you use sbt 0.13.5 or greater.

You must have a `tar` command available on your system.

### Configuring the Plugin

You may set the desired JDK version like so:

```
herokuJdkVersion in Compile := "1.7"
```

Valid values are `1.6`, `1.7`, and `1.8`. The default is `1.7`

## Hacking

In order to run the test suite, you must have the [Heroku Toolbelt](https://toolbelt.heroku.com/) installed. Then run:

```
$ sbt scripted
```