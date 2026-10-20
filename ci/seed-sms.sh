#!/usr/bin/env bash
# Puts three messages in the emulator's SMS inbox before the device tests run:
# a bank debit (should import), an OTP and a personal text (both must be ignored).
# Best effort: if an emulator image refuses, the one test that needs it is skipped, not failed.
set -u

adb root >/dev/null 2>&1 || true
sleep 3
adb wait-for-device
now=$(( $(date +%s) * 1000 ))

insert() {
  adb shell "content insert --uri content://sms/inbox --bind address:s:$1 --bind body:s:'$2' --bind date:l:$now --bind read:i:1" \
    || echo "::warning::could not seed an SMS from $1 on this emulator"
}

insert "VM-HDFCBK-S" "Sent Rs.486.00 From HDFC Bank A/C *4821 To SWIGGY On 18/10/26 Ref 629112345678 Not You? Call 18002586161"
insert "VM-HDFCBK-T" "123456 is OTP for txn of INR 2,000.00 at AMAZON on HDFC Bank card ending 9012. Do not share OTP"
insert "+919876543210" "Rs 500 sent to you for dinner thanks"

echo "Inbox after seeding:"
adb shell "content query --uri content://sms/inbox --projection address" || true
