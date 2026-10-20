#!/usr/bin/env bash
# Checks the release APK works on devices with 16 KB memory pages (Android 15+, required by Google Play):
# the APK is 16 KB zip-aligned and every 64-bit native library has 16 KB ELF LOAD alignment.
set -euo pipefail
APK=app/build/outputs/apk/release/app-release.apk
BT="$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)"
"${BT}zipalign" -c -P 16 4 "$APK" && echo "APK zip alignment: 16 KB OK"
rm -rf so && mkdir so && unzip -q -o "$APK" 'lib/*' -d so
fail=0
for f in so/lib/arm64-v8a/*.so so/lib/x86_64/*.so; do
  [ -f "$f" ] || continue
  align=$(readelf -lW "$f" | awk '$1=="LOAD"{print $NF}' | sort -u | tr '\n' ' ')
  echo "$f: LOAD alignment $align"
  for a in $align; do
    [ $((a)) -ge 16384 ] || fail=1
  done
done
if [ "$fail" -ne 0 ]; then
  echo "::error::A 64-bit native library is not 16 KB aligned"
  exit 1
fi
echo "All 64-bit native libraries are 16 KB aligned"
