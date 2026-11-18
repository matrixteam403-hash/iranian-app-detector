#!/bin/sh
# Minimal, dependency-free Gradle wrapper launcher.
# (The official wrapper jar is not vendored; install Gradle 8.7 or newer and run
# `gradle wrapper --gradle-version 8.7` once to generate the standard wrapper.)

APP_HOME=$(cd "$(dirname "$0")" && pwd)
GRADLE_BIN=""

if [ -n "$GRADLE_HOME" ] && [ -x "$GRADLE_HOME/bin/gradle" ]; then
  GRADLE_BIN="$GRADLE_HOME/bin/gradle"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_BIN=$(command -v gradle)
elif [ -x "$APP_HOME/../../toolchain/gradle-8.7/bin/gradle" ]; then
  GRADLE_BIN="$APP_HOME/../../toolchain/gradle-8.7/bin/gradle"
fi

if [ -z "$GRADLE_BIN" ]; then
  echo "Gradle not found. Install Gradle 8.7+ or set GRADLE_HOME." >&2
  exit 1
fi

exec "$GRADLE_BIN" "$@"
