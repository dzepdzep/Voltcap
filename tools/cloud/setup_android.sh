#!/usr/bin/env bash
# Cloud development setup (Claude Code cloud sessions / scheduled runs; also usable on any Linux box).
# Installs the Android SDK pieces the project builds against, the same versions as CI (verify-update-310.yml):
# platforms;android-36, build-tools;36.0.0, ndk;28.2.13676358, cmake;3.22.1. Idempotent: a second run only checks.
# No credentials are read or written. Signing keys / service properties are NOT needed: debug + -PvoltcapCiWithoutDiscord=true.
#   bash tools/cloud/setup_android.sh            # SDK into $ANDROID_HOME (default ~/android-sdk), writes local.properties
#   bash tools/cloud/setup_android.sh --no-ndk   # unit tests only (no native build)
set -euo pipefail
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
NDK=1; [ "${1:-}" = "--no-ndk" ] && NDK=0
CLT_ZIP=commandlinetools-linux-13114758_latest.zip
mkdir -p "$SDK"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp=$(mktemp -d)
  curl -fsSL --retry 3 -o "$tmp/clt.zip" "https://dl.google.com/android/repository/$CLT_ZIP"
  (cd "$tmp" && unzip -q clt.zip)
  mkdir -p "$SDK/cmdline-tools"; rm -rf "$SDK/cmdline-tools/latest"; mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"; rm -rf "$tmp"
fi
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKM" --sdk_root="$SDK" --licenses > /dev/null 2>&1 || true
PKGS=("platform-tools" "platforms;android-36" "build-tools;36.0.0")
[ $NDK = 1 ] && PKGS+=("ndk;28.2.13676358" "cmake;3.22.1")
"$SDKM" --sdk_root="$SDK" --install "${PKGS[@]}" > /dev/null
root=$(cd "$(dirname "$0")/../.." && pwd)
grep -q '^sdk.dir=' "$root/local.properties" 2>/dev/null || echo "sdk.dir=$SDK" >> "$root/local.properties"
echo "Android SDK ready at $SDK ($(ls "$SDK/platforms" | tr '\n' ' '))"
echo "export ANDROID_HOME=$SDK"
# (676d, owner decision 3A: the stable QA signing key from the cloud environment's private variable - never printed. Without it
# a QA build here would be signed with a new random debug key and could not update the installed QA app.)
if [ -n "${VOLTCAP_QA_DEBUG_KEYSTORE_B64:-}" ]; then bash "$root/tools/cloud/qa_key.sh" restore || echo "QA key: restore FAILED - build no QA APK for devices"
else bash "$root/tools/cloud/qa_key.sh" check 2>/dev/null || echo "QA key: not set up here (VOLTCAP_QA_DEBUG_KEYSTORE_B64 missing) - build no QA APK for devices"; fi
