#!/bin/sh
# Builds the TV home screen, bundles it into the app as assets/manga-home.apk, then builds OpenRemoTV.
# Usage: ANDROID_HOME=<sdk> tools/build-all.sh
set -e
cd "$(dirname "$0")/.."
(cd homescreen && ./gradlew --no-daemon -q assembleRelease)
cp homescreen/app/build/outputs/apk/release/app-release.apk app/src/main/assets/manga-home.apk
./gradlew --no-daemon -q assembleRelease
echo "OpenRemoTV: app/build/outputs/apk/release/app-release.apk"
