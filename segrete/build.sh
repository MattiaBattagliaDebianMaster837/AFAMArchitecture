#!/usr/bin/env bash
# Compila l'APK di "Segrete di Mattia" senza Gradle: aapt2 + javac + d8/dx + zipalign + apksigner.
# Su Debian/Ubuntu bastano: apt install android-sdk-platform-23 aapt apksigner zipalign dalvik-exchange
# Con un Android SDK completo basta impostare ANDROID_HOME.
set -euo pipefail
cd "$(dirname "$0")"

VERSION_CODE=${VERSION_CODE:-1}
VERSION_NAME=${VERSION_NAME:-1.0}
OUT=${OUT:-../release/SegreteDiMattia.apk}
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/lib/android-sdk}}

# android.jar: la piattaforma più recente disponibile
ANDROID_JAR=${ANDROID_JAR:-$(ls -d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)}
[ -f "$ANDROID_JAR" ] || { echo "android.jar non trovato (imposta ANDROID_HOME o ANDROID_JAR)"; exit 1; }
BT=$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)
tool() { if [ -n "$BT" ] && [ -x "$BT/$1" ]; then echo "$BT/$1"; elif command -v "$1" >/dev/null; then command -v "$1"; fi; }
AAPT2=$(tool aapt2); ZIPALIGN=$(tool zipalign); APKSIGNER=$(tool apksigner); D8=$(tool d8)
DX=$(tool dalvik-exchange); [ -n "$DX" ] || DX=$(tool dx)
for t in AAPT2 ZIPALIGN APKSIGNER; do [ -n "${!t}" ] || { echo "manca $t"; exit 1; }; done
echo "android.jar: $ANDROID_JAR"

B=build
rm -rf "$B"; mkdir -p "$B/classes" "$B/gen" "$B/assets/www"
cp -r web/. "$B/assets/www/"

"$AAPT2" compile --dir android/res -o "$B/res.zip"
"$AAPT2" link -o "$B/unsigned.apk" -I "$ANDROID_JAR" \
  --manifest android/AndroidManifest.xml \
  --min-sdk-version 24 --target-sdk-version 35 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  -A "$B/assets" --java "$B/gen" "$B/res.zip"

javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$ANDROID_JAR" \
  -d "$B/classes" $(find android/src "$B/gen" -name '*.java')

if [ -n "$D8" ]; then
  "$D8" --release --min-api 24 --lib "$ANDROID_JAR" --output "$B" $(find "$B/classes" -name '*.class')
else
  "$DX" --dex --min-sdk-version=24 --output="$B/classes.dex" "$B/classes"
fi
(cd "$B" && zip -q unsigned.apk classes.dex)

"$ZIPALIGN" -f -p 4 "$B/unsigned.apk" "$B/aligned.apk"
mkdir -p "$(dirname "$OUT")"
"$APKSIGNER" sign --ks android/segrete-release.jks --ks-key-alias segrete \
  --ks-pass pass:segrete2026 --key-pass pass:segrete2026 --out "$OUT" "$B/aligned.apk"
"$APKSIGNER" verify "$OUT"
rm -f "$OUT.idsig"
echo "APK pronto: $OUT ($(du -h "$OUT" | cut -f1))"
