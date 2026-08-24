#!/bin/sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_DIR="$APP_HOME/gradle/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v9.2.1/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_SHA256="423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "[ClaimShift] Gradle wrapper JAR is missing. Downloading the official Gradle 9.2.1 wrapper..."
  mkdir -p "$WRAPPER_DIR"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "$WRAPPER_URL" -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$WRAPPER_JAR" "$WRAPPER_URL"
  else
    echo "ERROR: curl or wget is required to bootstrap the Gradle wrapper." >&2
    exit 1
  fi

  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL=$(sha256sum "$WRAPPER_JAR" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL=$(shasum -a 256 "$WRAPPER_JAR" | awk '{print $1}')
  else
    rm -f "$WRAPPER_JAR"
    echo "ERROR: sha256sum or shasum is required to verify the Gradle wrapper." >&2
    exit 1
  fi

  if [ "$ACTUAL" != "$WRAPPER_SHA256" ]; then
    rm -f "$WRAPPER_JAR"
    echo "ERROR: Gradle wrapper checksum mismatch." >&2
    exit 1
  fi
fi

if [ -n "${JAVA_HOME:-}" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE="java"
fi

exec "$JAVA_EXE" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
