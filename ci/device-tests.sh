#!/usr/bin/env bash
# Runs inside the emulator step: waits until Android is fully up, seeds the SMS inbox,
# then runs the instrumented tests.
set -o pipefail
mkdir -p logs

# "Boot completed" can arrive before the user is unlocked and credential-encrypted storage is
# available (seen on Android 17 images). Wait for all three signals before touching the device.
wait_ready() {
  adb wait-for-device
  for i in $(seq 1 120); do
    boot=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    if [ "$boot" = "1" ] \
      && adb shell dumpsys user 2>/dev/null | grep -q "RUNNING_UNLOCKED" \
      && adb shell pm path android >/dev/null 2>&1; then
      echo "Device ready after about $((i * 5)) s"
      adb shell input keyevent 82 >/dev/null 2>&1 || true
      sleep 5
      return 0
    fi
    sleep 5
  done
  echo "Device still not fully ready after 10 minutes; continuing anyway"
}

wait_ready 2>&1 | tee logs/ready.log
bash ci/seed-sms.sh 2>&1 | tee logs/seed.log
wait_ready >/dev/null 2>&1   # seeding may restart adbd as root
./gradlew :app:connectedDebugAndroidTest --stacktrace 2>&1 | tee logs/device.log
