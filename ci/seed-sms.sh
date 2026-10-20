#!/usr/bin/env bash
# Puts three messages in the emulator's SMS inbox before the device tests run:
# a bank debit (should import), an OTP and a personal text (both must be ignored).
#
# Android silently drops SMS-provider writes from apps that aren't the default SMS app unless their
# WRITE_SMS app-op is allowed, so the shell is granted that op first. If the inbox is still empty,
# the same is tried as root. Best effort: if an image refuses both, the one test that needs the
# seeded inbox is skipped, not failed.
set -u
now=$(( $(date +%s) * 1000 ))

insert() {
  adb shell "content insert --uri content://sms/inbox --bind address:s:$1 --bind body:s:'$2' --bind date:l:$now --bind read:i:1" \
    || echo "could not insert an SMS from $1"
}

seed() {
  insert "VM-HDFCBK-S" "Sent Rs.486.00 From HDFC Bank A/C *4821 To SWIGGY On 18/10/26 Ref 629112345678 Not You? Call 18002586161"
  insert "VM-HDFCBK-T" "123456 is OTP for txn of INR 2,000.00 at AMAZON on HDFC Bank card ending 9012. Do not share OTP"
  insert "+919876543210" "Rs 500 sent to you for dinner thanks"
}

inbox_count() {
  adb shell "content query --uri content://sms/inbox --projection address" 2>/dev/null | grep -c "Row:" || true
}

adb wait-for-device
echo "Attempt 1: shell user with WRITE_SMS allowed"
adb shell appops set com.android.shell WRITE_SMS allow || true
seed
if [ "$(inbox_count)" -eq 0 ]; then
  echo "Attempt 2: root"
  adb root >/dev/null 2>&1 || true
  sleep 3
  adb wait-for-device
  adb shell appops set com.android.shell WRITE_SMS allow || true
  adb shell appops set --uid 0 WRITE_SMS allow 2>/dev/null || true
  seed
fi

echo "Inbox after seeding:"
adb shell "content query --uri content://sms/inbox --projection address" || true
