#!/usr/bin/env bash

# fail hard
set -o pipefail
# fail harder
set -eu

sbt "^^ 0.13.16" publishLocal
sbt "^^ 1.0.0" publishLocal
sbt release
