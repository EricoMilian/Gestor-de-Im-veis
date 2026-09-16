#!/bin/sh
set -e
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.7
DIST_NAME="gradle-${GRADLE_VERSION}-bin.zip"
DIST_URL="https://services.gradle.org/distributions/${DIST_NAME}"
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-${GRADLE_VERSION}"
INSTALL_DIR="${CACHE_DIR}/gradle-${GRADLE_VERSION}"
ZIP_FILE="${CACHE_DIR}/${DIST_NAME}"
mkdir -p "$CACHE_DIR"
if [ ! -x "$INSTALL_DIR/bin/gradle" ]; then
  echo "Baixando Gradle ${GRADLE_VERSION}..."
  rm -rf "$INSTALL_DIR" "$ZIP_FILE.tmp"
  if command -v curl >/dev/null 2>&1; then
    curl -L --fail --retry 3 -o "$ZIP_FILE.tmp" "$DIST_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP_FILE.tmp" "$DIST_URL"
  else
    echo "Erro: curl/wget não encontrado." >&2
    exit 1
  fi
  mv "$ZIP_FILE.tmp" "$ZIP_FILE"
  command -v unzip >/dev/null 2>&1 || { echo "Erro: unzip não encontrado." >&2; exit 1; }
  unzip -q "$ZIP_FILE" -d "$CACHE_DIR"
fi
exec "$INSTALL_DIR/bin/gradle" "$@"
