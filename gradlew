#!/usr/bin/env sh
set -eu
VERSION="8.11.1"
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CACHE_DIR="$ROOT_DIR/.gradle-dist"
GRADLE_DIR="$CACHE_DIR/gradle-$VERSION"
ZIP="$CACHE_DIR/gradle-$VERSION-bin.zip"
if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"
  if [ ! -f "$ZIP" ]; then
    URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
    if command -v curl >/dev/null 2>&1; then
      curl -fL "$URL" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "$URL"
    else
      echo "curl or wget is required for the first Gradle run." >&2
      exit 1
    fi
  fi
  unzip -q -o "$ZIP" -d "$CACHE_DIR"
fi
exec "$GRADLE_DIR/bin/gradle" "$@"
