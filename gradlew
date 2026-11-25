#!/bin/sh
# Gradle bootstrap: downloads the pinned official Gradle distribution when absent.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
GRADLE_VERSION=8.13.2
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME/.gradle"}
GRADLE_HOME="$GRADLE_USER_HOME/pixpronto-gradle/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$GRADLE_USER_HOME/pixpronto-gradle"
  ZIP="$GRADLE_USER_HOME/pixpronto-gradle/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$ZIP"
    else echo 'Install curl or wget to download Gradle.' >&2; exit 1; fi
  fi
  unzip -q -o "$ZIP" -d "$GRADLE_USER_HOME/pixpronto-gradle"
fi
# Generate the official Gradle Wrapper JAR/scripts on first use from this pinned distribution.
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  "$GRADLE_HOME/bin/gradle" -p "$APP_HOME" wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin
fi
exec "$GRADLE_HOME/bin/gradle" -p "$APP_HOME" "$@"
