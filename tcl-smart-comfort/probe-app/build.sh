#!/bin/bash
# Builds the Smart Comfort probe APK without Gradle: aapt2 + javac + d8 + apksigner.
# Needs: JDK, Android SDK with platforms;android-34 and build-tools;34.0.0 (ANDROID_HOME).
set -euo pipefail
# d8 8.2 cannot read inner classes compiled by javac 27; use JDK 17.
export JAVA_HOME=${JAVA_HOME_17:-/opt/homebrew/opt/openjdk@17} PATH=${JAVA_HOME_17:-/opt/homebrew/opt/openjdk@17}/bin:$PATH
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
B=build
rm -rf "$B"; mkdir -p "$B/classes" "$B/dex"

"$BT/aapt2" link -o "$B/unsigned.apk" -I "$JAR" --manifest AndroidManifest.xml
javac --release 11 -classpath "$JAR" -d "$B/classes" $(find src -name '*.java')
"$BT/d8" --min-api 30 --lib "$JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')
(cd "$B/dex" && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$B/unsigned.apk" "$B/aligned.apk"

KS=${KEYSTORE:-$HOME/.android/smartcomfort-debug.keystore}
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias probe \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Smart Comfort Debug" >/dev/null
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$B/smartcomfort-probe.apk" "$B/aligned.apk"
echo "OK: $B/smartcomfort-probe.apk"
