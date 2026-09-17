#!/bin/bash
# Manual TwinStick build: javac -> d8 -> aapt2 link -> zipalign -> apksigner
# (Gradle daemon does not run in this sandbox.)
set -e
export PATH="$HOME/workspace/jdk/jdk-17.0.20.1+1/bin:$PATH"
SDK=~/workspace/android-sdk
BT=$SDK/build-tools/34.0.0
JDK=~/workspace/jdk/jdk-17.0.20.1+1
PROJ=~/workspace/twinstick-controller
OUT=/tmp/build-twinstick-v1
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex"

echo "== javac =="
$JDK/bin/javac -source 17 -target 17 \
  -classpath "$SDK/platforms/android-34/android.jar" \
  -d "$OUT/classes" \
  "$PROJ/app/src/main/java/com/twinstick/controller/"*.java

echo "== d8 =="
$BT/d8 --lib "$SDK/platforms/android-34/android.jar" \
  --output "$OUT/dex" \
  $(find "$OUT/classes" -name "*.class")

echo "== aapt2 link =="
$BT/aapt2 link -o "$OUT/base.apk" \
  -I "$SDK/platforms/android-34/android.jar" \
  --manifest "$PROJ/app/src/main/AndroidManifest.xml" \
  --min-sdk-version 28 --target-sdk-version 34 \
  --version-code 5 --version-name "1.0"

echo "== add classes.dex =="
(cd "$OUT/dex" && zip -q "$OUT/base.apk" classes.dex)

echo "== zipalign =="
$BT/zipalign -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "== apksigner =="
$BT/apksigner sign \
  --ks "$PROJ/build-manual/debug.keystore" --ks-pass pass:android \
  --ks-key-alias androiddebugkey --key-pass pass:android \
  --out "$OUT/twinstick-controller-v1.0.apk" "$OUT/aligned.apk"

echo "== verify =="
$BT/apksigner verify "$OUT/twinstick-controller-v1.0.apk" && echo "SIGNATURE OK"
$BT/aapt dump badging "$OUT/twinstick-controller-v1.0.apk" | head -3
ls -la "$OUT/twinstick-controller-v1.0.apk"
