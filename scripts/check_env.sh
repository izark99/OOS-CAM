#!/usr/bin/env bash
# Gate G0 part 1: the build environment is complete.
set -u
fail=0
REPO="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
jv=$(java -version 2>&1 | head -1); echo "java: $jv"
echo "$jv" | grep -qE '"(1[7-9]|2[0-9])' || { echo "FAIL: JDK 17+ needed"; fail=1; }
[ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ] && echo "sdkmanager: ok" || { echo "FAIL: sdkmanager missing (run scripts/setup_cloud.sh)"; fail=1; }
p=$(ls "$ANDROID_HOME/platforms" 2>/dev/null | sort -V | tail -1); [ -n "$p" ] && echo "platform: $p" || { echo "FAIL: no Android platform"; fail=1; }
b=$(ls "$ANDROID_HOME/build-tools" 2>/dev/null | sort -V | tail -1); [ -n "$b" ] && echo "build-tools: $b" || { echo "FAIL: no build-tools"; fail=1; }
[ -f "$REPO/local.properties" ] && echo "local.properties: ok" || { echo "FAIL: local.properties missing"; fail=1; }
git -C "$REPO" check-ignore -q local.properties && echo "local.properties ignored: ok" || { echo "FAIL: local.properties not git-ignored"; fail=1; }
[ -x "$REPO/gradlew" ] && echo "gradlew: ok" || { echo "FAIL: gradlew missing"; fail=1; }
python3 --version
[ $fail -eq 0 ] && echo "RESULT: PASS" || echo "RESULT: FAIL"
exit $fail
