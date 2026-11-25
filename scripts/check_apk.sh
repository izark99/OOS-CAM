#!/usr/bin/env bash
# Gate G5: release APK size and permissions.
cd "$(dirname "$0")/.."
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
apk=$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | head -1)
[ -n "$apk" ] || { echo "FAIL: no release APK"; exit 1; }
size=$(stat -c %s "$apk"); mb=$(python3 -c "print(round($size/1e6,2))")
echo "apk: $apk size: ${mb} MB"
aapt2=$(ls "$ANDROID_HOME"/build-tools/*/aapt2 | sort -V | tail -1)
perms=$("$aapt2" dump permissions "$apk")
echo "$perms"
fail=0
python3 -c "import sys; sys.exit(0 if $size <= 25_000_000 else 1)" || { echo "FAIL: APK over 25 MB"; fail=1; }
if echo "$perms" | grep -q 'android.permission.INTERNET'; then echo "INTERNET: present"; fail=1; else echo "INTERNET: absent"; fi
for p in CAMERA RECORD_AUDIO POST_NOTIFICATIONS FOREGROUND_SERVICE_CAMERA FOREGROUND_SERVICE_MICROPHONE WAKE_LOCK; do
  echo "$perms" | grep -q "android.permission.$p" || { echo "FAIL: missing $p"; fail=1; }
done
[ $fail -eq 0 ] && echo "RESULT: PASS" || echo "RESULT: FAIL"
exit $fail
