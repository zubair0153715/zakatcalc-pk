#!/bin/bash
# Manual ZakatCalc PK build — no Gradle (Gradle daemon IPC + dep downloads blocked in this sandbox).
# Pipeline: aapt2 compile/link -> javac R.java -> kotlinc -> d8 -> zipalign -> apksigner
# Framework APIs only (no appcompat/material).
set -e

APP=~/workspace/zakatcalc/app/src/main
BUILD=~/workspace/zakatcalc/build-manual
ANDROID_JAR=~/Android/platforms/android-34/android.jar
BT=~/Android/build-tools/34.0.0
KC=~/kotlin-compiler

rm -rf "$BUILD"
mkdir -p "$BUILD/compiled" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"

echo "==> aapt2 compile"
$BT/aapt2 compile --dir "$APP/res" -o "$BUILD/compiled.zip"

echo "==> aapt2 link"
$BT/aapt2 link -o "$BUILD/app-unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --min-sdk-version 24 --target-sdk-version 34 \
  "$BUILD/compiled.zip"

echo "==> javac (R.java)"
find "$BUILD/gen" -name "*.java" > "$BUILD/rjava.txt"
javac -source 17 -target 17 -cp "$ANDROID_JAR" -d "$BUILD/classes" @"$BUILD/rjava.txt"

echo "==> kotlinc"
find "$APP/java" -name "*.kt" > "$BUILD/kt.txt"
java -Xmx768m -Djava.net.preferIPv4Stack=true -cp "$KC/kotlin-compiler-embeddable.jar:$KC/kotlin-stdlib.jar:$KC/trove4j.jar:$KC/annotations.jar" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -cp "$ANDROID_JAR:$BUILD/classes:$KC/kotlin-stdlib.jar:$KC/annotations.jar" \
  -d "$BUILD/classes" \
  -jvm-target 17 -no-stdlib -no-reflect \
  @"$BUILD/kt.txt"

echo "==> d8 (dex)"
$BT/d8 --lib "$ANDROID_JAR" \
  --min-api 24 \
  --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name "*.class") \
  "$KC/kotlin-stdlib.jar" "$KC/annotations.jar"

echo "==> add classes.dex"
cd "$BUILD/dex" && zip -q -j "$BUILD/app-unsigned.apk" classes.dex && cd - > /dev/null

echo "==> keystore"
if [ ! -f "$BUILD/debug.keystore" ]; then
  keytool -genkeypair -keystore "$BUILD/debug.keystore" -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
fi

echo "==> zipalign"
$BT/zipalign -f 4 "$BUILD/app-unsigned.apk" "$BUILD/app-aligned.apk"

echo "==> apksigner"
$BT/apksigner sign --ks "$BUILD/debug.keystore" --ks-pass pass:android \
  --key-pass pass:android --out "$BUILD/ZakatCalcPK-v1.0-debug.apk" "$BUILD/app-aligned.apk"

echo "==> verify"
$BT/apksigner verify --print-certs "$BUILD/ZakatCalcPK-v1.0-debug.apk" | head -3
ls -la "$BUILD/ZakatCalcPK-v1.0-debug.apk"
cp "$BUILD/ZakatCalcPK-v1.0-debug.apk" ~/workspace/zakatcalc/ZakatCalcPK-v1.0-debug.apk
echo "BUILD OK -> ~/workspace/zakatcalc/ZakatCalcPK-v1.0-debug.apk"
