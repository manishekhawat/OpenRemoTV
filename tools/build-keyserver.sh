#!/bin/sh
# Rebuilds the TV helper (keyserver/src/KeyServer.java) into app/src/main/assets/keyserver.dex.
# Usage: ANDROID_HOME=<sdk> tools/build-keyserver.sh
set -e
cd "$(dirname "$0")/.."
out=keyserver/out
rm -rf "$out" && mkdir -p "$out"
javac -source 8 -target 8 -cp "$ANDROID_HOME/platforms/android-34/android.jar" -d "$out" keyserver/src/KeyServer.java
"$ANDROID_HOME/build-tools/34.0.0/d8" --min-api 26 --output "$out" "$out"/*.class
cp "$out/classes.dex" app/src/main/assets/keyserver.dex
