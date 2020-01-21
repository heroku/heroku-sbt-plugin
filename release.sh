#!/usr/bin/env bash

# fail hard
set -o pipefail
# fail harder
set -eu

sbt "^^ 0.13.18" publishLocal
sbt "^^ 1.0.0" publishLocal
sbt release

echo "Now make sure you update these articles and projects:

    https://devcenter.heroku.com/articles/deploying-scala-and-play-applications-with-the-heroku-sbt-plugin
    https://devcenter.heroku.com/articles/deploy-scala-and-play-applications-to-heroku-from-jenkins-ci
    https://devcenter.heroku.com/articles/deploy-scala-and-play-applications-to-heroku-from-travis-ci
    https://github.com/jkutner/heroku-jenkins-scala-example
    https://github.com/jkutner/travis-heroku-scala-example
    https://github.com/kissaten/sbt-heroku-play-example
"
