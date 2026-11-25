#!/usr/bin/env bash
# Run gates G0..GN in order; stop at the first failure.
set -u
N="${1:?usage: scripts/check.sh <phase 0-5>}"
cd "$(dirname "$0")/.."
run() { echo "\$ $*"; "$@"; c=$?; echo "exit: $c"; [ $c -eq 0 ] || { echo "GATE FAILED: $*"; exit $c; }; }
for g in $(seq 0 "$N"); do
  echo "== G$g =="
  case $g in
    0) run scripts/check_env.sh; run ./gradlew --console=plain assembleDebug lint ;;
    1) run ./gradlew --console=plain :core:test; run python3 testcase/run_acceptance.py --suite core ;;
    2) run ./gradlew --console=plain :app:testDebugUnitTest --tests '*Capture*' ;;
    3) run ./gradlew --console=plain testDebugUnitTest; run python3 testcase/run_acceptance.py --suite all ;;
    4) run ./gradlew --console=plain verifyRoborazziDebug; run python3 scripts/check_screens.py; run scripts/check_tokens.sh ;;
    5) run ./gradlew --console=plain assembleRelease; run scripts/check_apk.sh ;;
  esac
done
echo "ALL GATES G0..G$N PASS"
