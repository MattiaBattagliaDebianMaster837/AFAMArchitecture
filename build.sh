#!/usr/bin/env bash
# Builds release/PixelQuest.apk without the Android SDK / Gradle.
#
# Toolchain (downloaded once from Maven Central into .deps/, verified by SHA-256):
#   - Robolectric android-all (API 34)  -> android.jar to compile against
#   - dalvik-dx                          -> .class -> classes.dex
#   - apksig                             -> APK Signature Scheme v2
# The binary manifest and resources.arsc are produced by tools/apkbuild.py.
#
# Requirements: JDK 17+ (javac/java/keytool), python3, curl.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
DEPS="$ROOT/.deps"
BUILD="$ROOT/build"
M="https://repo1.maven.org/maven2"
mkdir -p "$DEPS"

fetch() {
    local name="$1" url="$2" sha="$3"
    if [ ! -f "$DEPS/$name" ]; then
        echo "downloading $name"
        curl -fsSL -o "$DEPS/$name.tmp" "$url"
        mv "$DEPS/$name.tmp" "$DEPS/$name"
    fi
    echo "$sha  $DEPS/$name" | sha256sum -c --quiet -
}

fetch android-all.jar "$M/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar" \
    6be2218c6a53fe3c57bc22ebdc723edcb7270a8a6f187545708aa5c0ed813977
fetch dalvik-dx.jar "$M/com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar" \
    1e4b645628e3bdb097b5331d669e177ef235a551582a8c646dbe36865e541907
fetch apksig.jar "$M/com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar" \
    9637078c0016244e4be0941836295365a7e2e5b164c59cb7885783c40460bfee

quiet() { grep -v -e '^Picked up JAVA_TOOL_OPTIONS' || true; }

echo "== levels"
python3 tools/genlevels.py levels/levels.txt app/src/it/pixelquest/core/Levels.java

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/desktop" "$BUILD/icons" "$BUILD/tools"

echo "== icons"
javac -Xlint:-options --release 8 -d "$BUILD/desktop" app/src/it/pixelquest/core/*.java desktop/src/it/pixelquest/core/*.java desktop/src/it/pixelquest/desktop/*.java 2>&1 | quiet
java -cp "$BUILD/desktop" it.pixelquest.desktop.IconGen "$BUILD/icons" 2>&1 | quiet

echo "== compile"
javac -Xlint:-options --release 8 -encoding UTF-8 -cp "$DEPS/android-all.jar" -d "$BUILD/classes" \
    $(find app/src -name '*.java') 2>&1 | quiet
test -f "$BUILD/classes/it/pixelquest/game/MainActivity.class"

echo "== dex"
java -cp "$DEPS/dalvik-dx.jar" com.android.dx.command.Main --dex --min-sdk-version=24 \
    --output="$BUILD/classes.dex" "$BUILD/classes" 2>&1 | quiet
test -f "$BUILD/classes.dex"

echo "== package"
python3 tools/apkbuild.py --manifest app/AndroidManifest.xml --dex "$BUILD/classes.dex" \
    --package it.pixelquest.game --icons "$BUILD/icons" --out "$BUILD/unsigned.apk"

echo "== sign"
KS="$ROOT/keystore/pixelquest-debug.p12"
if [ ! -f "$KS" ]; then
    mkdir -p "$ROOT/keystore"
    keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass android -keypass android \
        -alias pixelquest -keyalg RSA -keysize 2048 -validity 20000 -dname "CN=Pixel Quest" 2>&1 | quiet
fi
javac -Xlint:-options -cp "$DEPS/apksig.jar" -d "$BUILD/tools" tools/ApkSign.java 2>&1 | quiet
java --add-exports java.base/sun.security.x509=ALL-UNNAMED \
     --add-exports java.base/sun.security.pkcs=ALL-UNNAMED \
     --add-exports java.base/sun.security.util=ALL-UNNAMED \
     -cp "$DEPS/apksig.jar:$BUILD/tools" ApkSign "$BUILD/unsigned.apk" "$BUILD/PixelQuest.apk" "$KS" android pixelquest 24 2>&1 | quiet

mkdir -p release
cp "$BUILD/PixelQuest.apk" release/PixelQuest.apk
ls -l release/PixelQuest.apk
echo "OK -> release/PixelQuest.apk"
