#!/usr/bin/env bash
# Runs inside the emulator step: seeds the SMS inbox, then runs the instrumented tests.
set -o pipefail
mkdir -p logs
bash ci/seed-sms.sh 2>&1 | tee logs/seed.log
./gradlew :app:connectedDebugAndroidTest --stacktrace 2>&1 | tee logs/device.log
