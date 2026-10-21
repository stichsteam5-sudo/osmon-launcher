#!/bin/bash
# Builds Osmon.apk with plain Android SDK tools (no Gradle).
set -euo pipefail
cd "$(dirname "$0")"
SDK=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf build && mkdir -p build/gen build/obj build/dex

aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I "$SDK"
if ! javac -nowarn -source 8 -target 8 -bootclasspath "$SDK" -encoding UTF-8 \
  -d build/obj $(find src build/gen -name '*.java') 2> build/javac.log; then
  grep -v "^warning: \[options\]" build/javac.log; echo "BUILD FAILED"; exit 1
fi
dalvik-exchange --dex --min-sdk-version=23 --output=build/dex/classes.dex build/obj
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$SDK" -F build/unsigned.apk
(cd build/dex && aapt add ../unsigned.apk classes.dex >/dev/null)
zipalign -f 4 build/unsigned.apk build/aligned.apk

# Signing key: keep osmon.keystore safe — updates must be signed with the same key.
PASS="${OSMON_KEY_PASS:-changeit}"
if [ ! -f osmon.keystore ]; then
  keytool -genkeypair -keystore osmon.keystore -storepass "$PASS" -keypass "$PASS" -alias osmon \
    -keyalg RSA -keysize 2048 -validity 20000 -dname "CN=Osmon" >/dev/null 2>&1
fi
apksigner sign --ks osmon.keystore --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
  --out build/Osmon.apk build/aligned.apk
apksigner verify --print-certs build/Osmon.apk | head -2
ls -la build/Osmon.apk
