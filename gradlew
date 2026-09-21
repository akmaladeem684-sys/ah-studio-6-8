#!/bin/sh
#
# Self-bootstrapping Gradle launcher for AH Video Studio.
# It intentionally keeps the standard ./gradlew command usable even when
# gradle-wrapper.jar is not present in a transferred ZIP.
#
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION="9.3.1"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
DIST_ZIP="$DIST_DIR/gradle-${GRADLE_VERSION}-bin.zip"
DIST_HOME="$DIST_DIR/gradle-${GRADLE_VERSION}"

if [ ! -x "$DIST_HOME/bin/gradle" ]; then
  mkdir -p "$DIST_DIR"
  if [ ! -f "$DIST_ZIP" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    curl -fL --retry 3 --connect-timeout 20 \
      "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
      -o "$DIST_ZIP"
  fi
  tmp_dir="$DIST_DIR/.extract-$$"
  rm -rf "$tmp_dir"
  mkdir -p "$tmp_dir"
  unzip -q "$DIST_ZIP" -d "$tmp_dir"
  rm -rf "$DIST_HOME"
  mv "$tmp_dir/gradle-$GRADLE_VERSION" "$DIST_HOME"
  rm -rf "$tmp_dir"
fi

exec "$DIST_HOME/bin/gradle" "$@"
