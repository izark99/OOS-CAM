#!/usr/bin/env bash
# Cloud environment setup script for OOS Cam (paste "bash scripts/setup_cloud.sh"
# as the environment's setup script, or run it once by hand).
# Needs dl.google.com in the environment's network allowlist.
set -euo pipefail
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
TOOLS_ZIPS=("commandlinetools-linux-13114758_latest.zip" "commandlinetools-linux-11076708_latest.zip")
PLATFORMS=("android-36" "android-35")
BUILD_TOOLS=("36.0.0" "35.0.0")

mkdir -p "$ANDROID_HOME/cmdline-tools"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  ok=""
  for z in "${TOOLS_ZIPS[@]}"; do
    if curl -fsSL -o /tmp/cmdtools.zip "https://dl.google.com/android/repository/$z"; then ok=1; break; fi
  done
  [ -n "$ok" ] || { echo "BLOCKED: cannot download Android command-line tools from dl.google.com"; exit 2; }
  rm -rf /tmp/cmdtools && unzip -q /tmp/cmdtools.zip -d /tmp/cmdtools
  rm -rf "$ANDROID_HOME/cmdline-tools/latest" && mv /tmp/cmdtools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
fi
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKM" --licenses >/dev/null || true
installed=""
for i in 0 1; do
  if "$SDKM" "platform-tools" "platforms;${PLATFORMS[$i]}" "build-tools;${BUILD_TOOLS[$i]}" >/dev/null; then
    installed="${PLATFORMS[$i]} ${BUILD_TOOLS[$i]}"; break
  fi
done
[ -n "$installed" ] || { echo "BLOCKED: sdkmanager could not install a platform"; exit 2; }
grep -q ANDROID_HOME "$HOME/.bashrc" 2>/dev/null || {
  echo "export ANDROID_HOME=$ANDROID_HOME" >> "$HOME/.bashrc"
  echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> "$HOME/.bashrc"
}
REPO="$(cd "$(dirname "$0")/.." && pwd)"
echo "sdk.dir=$ANDROID_HOME" > "$REPO/local.properties"
echo "Installed: $installed at $ANDROID_HOME"
