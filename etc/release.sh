#!/usr/bin/env bash

# fail hard
set -o pipefail
# fail harder
set -eu

. ./etc/semver.sh

CUR_VERSION="$(grep "git.baseVersion :=" "version.sbt" | sed -E -e 's/git.baseVersion := //g' | sed 's/"//g')"

sbt "release v${CUR_VERSION}"

MAJOR=0
MINOR=0
PATCH=0
SPECIAL=""

semverParseInto "${CUR_VERSION}" MAJOR MINOR PATCH SPECIAL

NEW_VERSION="${MAJOR}.${MINOR}.$(($PATCH + 1))"

sed -e s/${CUR_VERSION}/${NEW_VERSION}/g version.sbt > version.sbt.tmp
mv version.sbt.tmp version.sbt