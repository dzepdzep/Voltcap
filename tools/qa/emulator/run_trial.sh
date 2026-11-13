#!/usr/bin/env bash
# 529m - emulator QA trial (see .github/workflows/emulator-qa-trial.yml). Runs on the CI host with one booted emulator.
# Isolated QA app only, synthetic QA fixtures (the app's own QA hooks), launches blocked by the QA guard as on handhelds.
# Every result is labelled EMULATOR. A step that did not run or produced no capture is NOT TESTED / BLOCKED, never PASS.
set -u
OUT=${1:-qa-emulator-out}; mkdir -p "$OUT/raw"
S="$OUT/summary.md"; P=com.shelf.launcher.qa
note() { echo "$*" | tee -a "$S"; }
A() { adb shell am start -n $P/com.shelf.launcher.MainActivity "$@" > /dev/null 2>&1; }
waitdone() { # dir tag seconds
  for _ in $(seq 1 $(( $3 / 5 ))); do adb shell "grep -q DONE $1/log.txt 2>/dev/null" && return 0; sleep 5; done; return 1; }
echo "# Emulator QA trial (EMULATOR evidence - not real-handheld)" > "$S"
note "commit: ${GITHUB_SHA:-local}  run: ${GITHUB_SERVER_URL:-}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"
adb wait-for-device
for _ in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 3; done
# A throwaway emulator with no lock credential: dismiss its swipe screen; the compact profile the KONKR has (960x640, 240 dpi).
adb shell settings put secure lockscreen.disabled 1 >/dev/null 2>&1; adb shell wm dismiss-keyguard >/dev/null 2>&1
adb shell wm size 960x640; adb shell wm density 240; adb shell settings put system font_scale 1.0
adb shell settings put system screen_off_timeout 1800000; adb shell svc power stayon true
sleep 3
{
  echo "android=$(adb shell getprop ro.build.version.release | tr -d '\r') sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "abi=$(adb shell getprop ro.product.cpu.abilist | tr -d '\r') native_bridge=$(adb shell getprop ro.dalvik.vm.native.bridge | tr -d '\r')"
  echo "wm_size=$(adb shell wm size | tr -d '\r' | tr '\n' ' ')"
  echo "wm_density=$(adb shell wm density | tr -d '\r' | tr '\n' ' ')"
  echo "font_scale=$(adb shell settings get system font_scale | tr -d '\r') animator=$(adb shell settings get global animator_duration_scale | tr -d '\r')"
} > "$OUT/profile.txt"
note '```'; cat "$OUT/profile.txt" | tee -a "$S"; note '```'
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
note "apk: $(basename "$APK") sha256=$(sha256sum "$APK" | cut -c1-64)"
note "apk native libs: $(unzip -l "$APK" | awk '{print $4}' | grep '^lib/' | cut -d/ -f2 | sort -u | tr '\n' ' ')"
aapt=$(ls "$ANDROID_HOME"/build-tools/*/aapt 2>/dev/null | tail -1)
[ -n "$aapt" ] && note "apk package: $("$aapt" dump badging "$APK" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
if ! adb install -r -g "$APK" > "$OUT/install.txt" 2>&1; then note "INSTALL: FAILED ($(tail -1 "$OUT/install.txt")) - every check BLOCKED"; exit 0; fi
note "INSTALL: OK; primaryCpuAbi=$(adb shell dumpsys package $P | sed -n 's/.*primaryCpuAbi=//p' | head -1 | tr -d '\r')"
adb logcat -c
A; sleep 12
adb exec-out screencap -p > "$OUT/launch.png"
PID=$(adb shell pidof $P | tr -d '\r')
adb logcat -d -b crash > "$OUT/crash_log.txt" 2>&1
adb logcat -d | grep -iE "UnsatisfiedLink|dlopen|native|FATAL|$P" | tail -200 > "$OUT/launch_log.txt"
if [ -z "$PID" ]; then note "LAUNCH: FAILED (no process; see crash_log.txt / launch_log.txt) - every check BLOCKED"; exit 0; fi
note "LAUNCH: running pid=$PID; crash buffer lines=$(wc -l < "$OUT/crash_log.txt")"
adb shell mkdir -p /data/local/tmp
for f in pop529.sh popclip.sh qaguard.sh evidence.sh rec.sh shelfpages529.sh; do adb push "tools/qa/device/$f" /data/local/tmp/ > /dev/null; done
run_pop() { # tag scale layouts
  adb shell "cd /data/local/tmp && sh pop529.sh run $1 '$2' '$3' OWN"
  if waitdone /sdcard/pc/$1 $1 420; then note "popup gate $1 ($2: $3): run finished"; else note "popup gate $1 ($2): DID NOT FINISH (NOT TESTED)"; fi
  adb shell "cd /data/local/tmp && sh pop529.sh restore $1"
  adb pull -a /sdcard/pc/$1 "$OUT/raw/" > /dev/null 2>&1; adb pull -a /sdcard/pc/${1}_w "$OUT/raw/" > /dev/null 2>&1
  # (First run 43517899154: the gate found no main screenshot - the run's own logs go into the summary now.)
  { echo '```'; echo "$1 log:"; adb shell cat /sdcard/pc/$1/log.txt /sdcard/pc/${1}_w/log.txt 2>&1 | tail -40; echo "files: $(adb shell ls /sdcard/pc/$1 2>&1 | tr '\n' ' ' | cut -c1-600)"; echo '```'; } | tee -a "$S"
}
run_pop e529a "font=1.3,density=1.15" "COVERDETAILS SHELF"
run_pop e529b off "COVERDETAILS SHELF"
adb shell "cd /data/local/tmp && TOUCH=1 setsid sh shelfpages529.sh run e529c 'font=1.3,density=1.15' 'COVERDETAILS SHELF' > /dev/null 2>&1 < /dev/null &"
if waitdone /sdcard/pc/e529c e529c 600; then note "page walk e529c (x1.3, touch, Motion Off): run finished"; else note "page walk e529c: DID NOT FINISH (NOT TESTED)"; fi
adb shell "cd /data/local/tmp && sh shelfpages529.sh restore e529c"
adb shell "cp /sdcard/Android/data/$P/files/qa_clip/e529c_* /sdcard/pc/e529c/ 2>/dev/null"
adb pull -a /sdcard/pc/e529c "$OUT/raw/" > /dev/null 2>&1
{ echo '```'; echo "e529c log:"; adb shell cat /sdcard/pc/e529c/log.txt 2>&1 | tail -40; echo '```'; } | tee -a "$S"
adb logcat -d -b crash > "$OUT/crash_log_end.txt" 2>&1
note "crash buffer at the end: $(grep -c 'FATAL EXCEPTION' "$OUT/crash_log_end.txt") FATAL"
python3 tools/qa/emulator/grade_trial.py "$OUT" "$APK" | tee -a "$S"
exit 0
