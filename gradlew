#!/usr/bin/env sh
set -eu
GRADLE_VERSION=8.9
BOOTSTRAP_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/focus-clock-bootstrap"
GRADLE_DIR="$BOOTSTRAP_ROOT/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  mkdir -p "$BOOTSTRAP_ROOT"
  ZIP_FILE="${TMPDIR:-/tmp}/focus-clock-gradle-$GRADLE_VERSION.zip"
  echo "Downloading Gradle $GRADLE_VERSION (internet required on this computer)..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP_FILE"
  elif command -v wget >/dev/null 2>&1; then
    wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$ZIP_FILE"
  else
    echo "Install curl or wget to download Gradle." >&2
    exit 1
  fi
  unzip -q -o "$ZIP_FILE" -d "$BOOTSTRAP_ROOT"
fi
exec "$GRADLE_DIR/bin/gradle" "$@"
