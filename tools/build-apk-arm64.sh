#!/usr/bin/env bash
#
# Builds a signed debug APK on an ARM64 Linux machine (for example inside
# Termux/OpenCode Mobile), where Google's x86_64 aapt2 cannot run.
#
# The normal Gradle build (./gradlew assembleDebug) is the primary build and
# works on any regular x86_64 machine. This script uses the Ubuntu/Debian
# aapt + d8 + zipalign + apksigner tool-chain instead of Gradle + aapt2, and
# produces the same app from the same sources.
#
# Requirements:
#   openjdk-17-jdk-headless, aapt, zipalign, apksigner
#   Android SDK platform android-34 (android.jar) and build-tools 34.0.0 (d8.jar)
#
# Usage: tools/build-apk-arm64.sh
set -euo pipefail
cd "$(dirname "$0")/.."

SDK="${ANDROID_SDK_ROOT:-/root/android-sdk}"
PLATFORM="$SDK/platforms/android-34/android.jar"
D8_JAR="$SDK/build-tools/34.0.0/lib/d8.jar"
OUT="build/manual"
VERSION_NAME="1.13"
VERSION_CODE="14"
MIN_SDK="23"
TARGET_SDK="34"

for tool in aapt zipalign apksigner javac java keytool; do
    command -v "$tool" >/dev/null || { echo "missing tool: $tool" >&2; exit 1; }
done
[ -f "$PLATFORM" ] || { echo "missing $PLATFORM" >&2; exit 1; }
[ -f "$D8_JAR" ] || { echo "missing $D8_JAR" >&2; exit 1; }

# Optional plain-JAR libraries. Any libs/*.jar is dexed together with the app,
# so a dependency that ships only classes (no Android resources) can be dropped
# in without touching this script. AndroidX/AAR libraries need aapt2 and are not
# supported by this aapt/d8 tool-chain; see libs/README.md.
LIBS=()
if [ -d libs ]; then
    for jar in libs/*.jar; do
        [ -e "$jar" ] && LIBS+=("$jar")
    done
fi
if [ ${#LIBS[@]} -gt 0 ]; then
    echo "==> libraries: ${LIBS[*]}"
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/logs"

# aapt v1 needs the package name in the manifest; AGP takes it from
# app/build.gradle instead, so inject it into a temporary copy.
MANIFEST="$OUT/AndroidManifest.xml"
sed 's|<manifest |<manifest package="com.likhon.keyboard" |' \
    app/src/main/AndroidManifest.xml > "$MANIFEST"

echo "==> resources + R.java"
aapt package -f -m \
    -J "$OUT/gen" \
    -M "$MANIFEST" \
    -S app/src/main/res \
    -I "$PLATFORM" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK"

echo "==> javac"
find app/src/main/java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 11 -target 11 -Xlint:-options \
    -classpath "$PLATFORM" \
    -d "$OUT/classes" \
    @"$OUT/sources.txt"

echo "==> d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
java -cp "$D8_JAR" com.android.tools.r8.D8 \
    --release --min-api "$MIN_SDK" --lib "$PLATFORM" \
    --output "$OUT/dex" \
    @"$OUT/classes.txt" "${LIBS[@]}"

echo "==> package resources"
aapt package -f \
    -M "$MANIFEST" \
    -S app/src/main/res \
    -A app/src/main/assets \
    -I "$PLATFORM" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    -F "$OUT/app.unaligned.apk"

(cd "$OUT/dex" && aapt add "../app.unaligned.apk" classes.dex >/dev/null)

echo "==> align"
zipalign -f 4 "$OUT/app.unaligned.apk" "$OUT/app.aligned.apk"

echo "==> sign"
mkdir -p keystore
if [ ! -f keystore/likhon-debug.jks ]; then
    keytool -genkeypair -noprompt \
        -keystore keystore/likhon-debug.jks \
        -storepass android -keypass android \
        -alias likhon -keyalg RSA -keysize 2048 -validity 10950 \
        -dname "CN=Likhon Keyboard, OU=Personal, O=Likhon, L=Dhaka, C=BD" \
        >/dev/null 2>&1
fi
apksigner sign \
    --ks keystore/likhon-debug.jks \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias likhon \
    --out "$OUT/LikhonKeyboard-$VERSION_NAME.apk" \
    "$OUT/app.aligned.apk"

apksigner verify --print-certs "$OUT/LikhonKeyboard-$VERSION_NAME.apk" | head -5

echo
echo "APK: $(pwd)/$OUT/LikhonKeyboard-$VERSION_NAME.apk"
echo "Size: $(du -h "$OUT/LikhonKeyboard-$VERSION_NAME.apk" | cut -f1)"

# Convenience for phones: put a copy where the file manager can find it.
DOWNLOAD="/sdcard/Download"
if [ -d "$DOWNLOAD" ]; then
    cp "$OUT/LikhonKeyboard-$VERSION_NAME.apk" "$DOWNLOAD/LikhonKeyboard-$VERSION_NAME.apk"
    echo "Copied to: $DOWNLOAD/LikhonKeyboard-$VERSION_NAME.apk"
fi
