#!/usr/bin/env bash
# 529m - emulator QA trial (see .github/workflows/emulator-qa-trial.yml). Runs on the CI host with one booted emulator.
# Isolated QA app only, synthetic QA fixtures (the app's own QA hooks), launches blocked by the QA guard as on handhelds.
# Every result is labelled EMULATOR. A step that did not run or produced no capture is NOT TESTED / BLOCKED, never PASS.
set -u
OUT=${1:-qa-emulator-out}; mkdir -p "$OUT/raw"
S="$OUT/summary.md"; P=com.shelf.launcher.qa
note() { echo "$*" | tee -a "$S"; }
A() { adb shell am start -n $P/com.shelf.launcher.MainActivity "$@" > /dev/null 2>&1; }
waitdone() { # dir tag seconds - stops at once when the emulator is gone (run 43520733405 waited out the job timeout)
  for _ in $(seq 1 $(( $3 / 5 ))); do
    [ "$(adb get-state 2>/dev/null)" = device ] || { note "EMULATOR OFFLINE while waiting for $2 - remaining checks NOT TESTED"; return 2; }
    adb shell "grep -q DONE $1/log.txt 2>/dev/null" && return 0; sleep 5; done; return 1; }
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
# (The handhelds' QA app has all-files access from its first setup; a fresh emulator install does not - the QA fixture
# library lives under shared storage. Granted to the isolated QA app on this throwaway emulator only.)
adb shell appops set $P MANAGE_EXTERNAL_STORAGE allow > /dev/null 2>&1
# (Run 43520733405: "no game matches 'Shadow of the Colossus'" - the handhelds' QA library holds empty stand-in ROM files
# a QA script put there; a fresh emulator has none. The same synthetic stand-ins: empty files with game-like names.)
QF=/sdcard/Android/data/$P/files/roms
adb shell "mkdir -p $QF/ps2 $QF/snes $QF/gba $QF/n64 $QF/psx" > /dev/null 2>&1
for f in "ps2/Shadow of the Colossus (USA).iso" "ps2/Okami (USA).iso" "snes/Super Metroid (USA).sfc" "snes/Chrono Trigger (USA).sfc" \
         "gba/Golden Sun (USA).gba" "n64/Mario Kart 64 (USA).z64" "psx/Vagrant Story (USA).bin" "snes/EarthBound (USA).sfc"; do
  adb shell "touch '$QF/$f'" > /dev/null 2>&1
done
note "synthetic library: $(adb shell "ls -R $QF | grep -c '\\.'" | tr -d '\r') empty stand-in files"
adb logcat -c
# 529p (#16 6868819840, deterministic first-run state): a fresh install opens on the first-start setup and its questions -
# answered once with the QA hook (done / no extra art / battery note seen), then the app is started again so it reads them.
A --ez voltcap_test_firstrun_done true; sleep 8
note "first-run hook: $(adb logcat -d -s VoltcapQA | grep -c 'firstrun done') ack"
adb shell am force-stop $P; sleep 1
A; sleep 12
adb exec-out screencap -p > "$OUT/launch.png"
PID=$(adb shell pidof $P | tr -d '\r')
adb logcat -d -b crash > "$OUT/crash_log.txt" 2>&1
adb logcat -d | grep -iE "UnsatisfiedLink|dlopen|native|FATAL|$P" | tail -200 > "$OUT/launch_log.txt"
if [ -z "$PID" ]; then note "LAUNCH: FAILED (no process; see crash_log.txt / launch_log.txt) - every check BLOCKED"; exit 0; fi
note "LAUNCH: running pid=$PID; crash buffer lines=$(wc -l < "$OUT/crash_log.txt") (FATAL: $(grep -c 'FATAL EXCEPTION' "$OUT/crash_log.txt"); processes: $(grep -o 'Process: [^,]*' "$OUT/crash_log.txt" | sort -u | tr '\n' ' '))"
adb shell mkdir -p /data/local/tmp
for f in pop529.sh popclip.sh qaguard.sh evidence.sh rec.sh shelfpages529.sh; do adb push "tools/qa/device/$f" /data/local/tmp/ > /dev/null; done
# 529p: preconditions before each sequence - emulator there, the QA app in front with nothing over its home (one visible
# QA window: an in-app dialog is a second window with the same title), not on the profile picker. A failed precondition stops
# the run: its sequence and every later one are NOT TESTED, with a screenshot of what was in front.
qawins() { adb shell dumpsys window windows | grep -E "Window #|isVisible=" | grep -A1 "Window #.*$P" | grep -c "isVisible=true"; }
precheck() { # name
  [ "$(adb get-state 2>/dev/null)" = device ] || { note "PRECONDITION $1: EMULATOR OFFLINE - this and later sequences NOT TESTED"; return 2; }
  A; sleep 3
  # (529s, run 43600821842: the "Who's playing?" picker comes up after the start-up intro - a B sent before it was up did
  # nothing and the picker was still there. It is answered as the handheld scripts do - B - once it is shown, up to 4 times
  # over ~16 s; anything else in front stays a failed precondition.)
  local mark tries=0 homes=0
  while :; do
    mark=$(adb shell cat /sdcard/Android/data/$P/files/voltcap_qa_screen 2>/dev/null | cut -d' ' -f1 | tr -d '\r')
    # (HOME twice, 4 s apart: the mark is HOME from the activity's start until the picker is shown after the intro)
    if [ "$mark" = HOME ]; then homes=$((homes + 1)); [ $homes -ge 2 ] && break; else homes=0; fi
    [ $tries -ge 5 ] && break
    [ "$mark" = PICKER ] && { adb shell input keyevent KEYCODE_BUTTON_B; note "precondition $1: picker shown - B"; }
    sleep 4; tries=$((tries + 1))
  done
  local foc; foc=$(adb shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r' | sed 's/.*u0 //;s/}.*//')
  local n; n=$(qawins | tr -d '\r')
  note "precondition $1: focus=$foc screen=$mark qa_windows=$n"
  if [ "$foc" != "$P/com.shelf.launcher.MainActivity" ] || [ "$mark" != HOME ] || [ "$n" != 1 ]; then
    adb exec-out screencap -p > "$OUT/precondition_$1.png"
    note "PRECONDITION $1 FAILED (want focus $P/com.shelf.launcher.MainActivity, screen HOME, one QA window) - this and later sequences NOT TESTED; see precondition_$1.png"
    return 1
  fi
  return 0
}
# (529s, run 43601933855: the emulator went offline during e529a; finish's `adb logcat` then waited for a device that never
# came back and the job ran into its 55-minute limit before the evidence upload. Every adb call here is bounded now, and
# nothing is asked of an emulator that is gone.)
finish() {
  if [ "$(timeout 10 adb get-state 2>/dev/null)" = device ]; then
    timeout 30 adb logcat -d -b crash > "$OUT/crash_log_end.txt" 2>&1
    note "crash buffer at the end: $(grep -c 'FATAL EXCEPTION' "$OUT/crash_log_end.txt") FATAL"
  else note "crash buffer at the end: NOT READ (emulator offline)"; fi
  python3 tools/qa/emulator/grade_trial.py "$OUT" "$APK" | tee -a "$S"; exit 0; }
run_pop() { # tag scale layouts
  timeout 300 adb shell "cd /data/local/tmp && sh pop529.sh run $1 '$2' '$3' OWN"
  if waitdone /sdcard/pc/$1 $1 420; then note "popup gate $1 ($2: $3): run finished"; else note "popup gate $1 ($2): DID NOT FINISH (NOT TESTED)"; fi
  [ "$(timeout 10 adb get-state 2>/dev/null)" = device ] || { note "EMULATOR OFFLINE after $1 - nothing pulled"; return; }
  timeout 60 adb shell "cd /data/local/tmp && sh pop529.sh restore $1"
  timeout 120 adb pull -a /sdcard/pc/$1 "$OUT/raw/" > /dev/null 2>&1; timeout 120 adb pull -a /sdcard/pc/${1}_w "$OUT/raw/" > /dev/null 2>&1
  # (First run 43517899154: the gate found no main screenshot - the run's own logs go into the summary now.)
  { echo '```'; echo "$1 log:"; timeout 30 adb shell cat /sdcard/pc/$1/log.txt /sdcard/pc/${1}_w/log.txt 2>&1 | tail -40; echo "files: $(adb shell ls /sdcard/pc/$1 2>&1 | tr '\n' ' ' | cut -c1-600)"; echo '```'; } | tee -a "$S"
}
precheck e529a || finish
run_pop e529a "font=1.3,density=1.15" "COVERDETAILS SHELF"
precheck e529b || finish
run_pop e529b off "COVERDETAILS SHELF"
precheck e529c || finish
adb shell "cd /data/local/tmp && TOUCH=1 setsid sh shelfpages529.sh run e529c 'font=1.3,density=1.15' 'COVERDETAILS SHELF' > /dev/null 2>&1 < /dev/null &"
if waitdone /sdcard/pc/e529c e529c 600; then note "page walk e529c (x1.3, touch, Motion Off): run finished"; else note "page walk e529c: DID NOT FINISH (NOT TESTED)"; fi
[ "$(timeout 10 adb get-state 2>/dev/null)" = device ] || { note "EMULATOR OFFLINE after e529c - nothing pulled"; finish; }
timeout 60 adb shell "cd /data/local/tmp && sh shelfpages529.sh restore e529c"
timeout 30 adb shell "cp /sdcard/Android/data/$P/files/qa_clip/e529c_* /sdcard/pc/e529c/ 2>/dev/null"
timeout 180 adb pull -a /sdcard/pc/e529c "$OUT/raw/" > /dev/null 2>&1
{ echo '```'; echo "e529c log:"; timeout 30 adb shell cat /sdcard/pc/e529c/log.txt 2>&1 | tail -40; echo '```'; } | tee -a "$S"
finish
