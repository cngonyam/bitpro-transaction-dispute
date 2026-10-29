#!/bin/sh
set -eu

mkdir -p /data

# shellcheck disable=SC2086
exec java ${JAVA_OPTS:-} -jar /app/app.jar
