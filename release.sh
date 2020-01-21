#!/usr/bin/env bash

set -euo pipefail

if [[ "$(git rev-parse --abbrev-ref @)" != "master" ]]; then
  read -p "You're not on the master branch, continue anyway? [y/n] " -n 1 -r
  echo
  if [[ "${REPLY}" != "y" ]]; then
    exit 1
  fi
fi

git fetch

if [[ "$(git rev-parse @)" != "$(git rev-parse "@{u}")" ]]; then
  echo "Warning: $(git rev-parse --abbrev-ref @) is not even with $(git rev-parse --abbrev-ref "@{u}"). Aborting release."
  exit 1
fi

sbt "^publishLocal"
sbt release

echo "Now make sure you update these articles and projects:

    https://devcenter.heroku.com/articles/deploying-scala-and-play-applications-with-the-heroku-sbt-plugin
    https://devcenter.heroku.com/articles/deploy-scala-and-play-applications-to-heroku-from-jenkins-ci
    https://github.com/jkutner/heroku-jenkins-scala-example
    https://github.com/jkutner/travis-heroku-scala-example
    https://github.com/kissaten/sbt-heroku-play-example
"
