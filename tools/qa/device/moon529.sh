#!/system/bin/sh
# Update 529 (owner, 13 Nov): Night menu ("Moonlight") motion - the same recordings and metrics before and after the motion
# work. Isolated QA app ONLY (com.shelf.launcher.qa), fixture library, launches blocked. Never wakes / unlocks: a key only
# while the QA app is in front, awake and unlocked (else the run stops). Owner settings: snapshot saved first, restored last.
#   sh moon529.sh run TAG        clips: enter, nav (+ rapid), category, popup (+ rapid A B), cold start; Full perf (nav / idle),
#                                Lite perf; x1.3, long names, missing art: home + popup shots. ~6 min.
#   sh moon529.sh restore TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
k() { gate; input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
picker() { grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_B 1.5; log "picker dismissed"; }; }
shot() { sleep ${2:-2}; gate; screencap -p $O/$1.png; log "shot $1"; }
perf() { dumpsys gfxinfo $P | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync|Number Slow UI thread" > $O/perf_$1.txt; log "perf $1: $(tr '\n' ' ' < $O/perf_$1.txt | cut -c1-200)"; }
. /data/local/tmp/rec.sh 2>/dev/null || . "$(dirname "$0")/rec.sh"
clip() { REC_SEG=9 rec_start $O/$1 ${2:-9}; sleep 0.6; }
endclip() { rec_stop; sleep 0.4; log "clip done"; }
case $MODE in
run)
  dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate; picker
  A --es voltcap_test_snapshot save; sleep 2
  A --es voltcap_test_artfix "delay=0"; sleep 1.5
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --ez voltcap_test_preview true --es voltcap_test_perf FULL --es voltcap_test_category ALL; sleep 1.5
  A --es voltcap_test_layout CLASSIC; sleep 3; picker
  # 1. entering the layout
  clip c1_enter 5; A --es voltcap_test_layout NIGHT; sleep 3.5; endclip; shot h1_home 0.5
  # 2. moving between games: steady, then very fast, then back
  clip c2_nav 9; k DPAD_DOWN 0.7; k DPAD_DOWN 0.7; k DPAD_DOWN 0.7
  for i in 1 2 3 4 5 6; do gate; input keyevent KEYCODE_DPAD_DOWN; sleep 0.08; done; sleep 1
  k DPAD_UP 0.35; k DPAD_UP 0.35; k DPAD_UP 1.2; endclip
  # 3. categories (R2 next, L2 back)
  clip c3_cat 7; k BUTTON_R2 1.4; k BUTTON_R2 1.4; k BUTTON_L2 1.6; endclip
  A --es voltcap_test_category ALL; sleep 1.5
  # 4. the game window: open, pages, close, then fast A B A B
  clip c4_pop 9; k DPAD_CENTER 1.8; k BUTTON_R1 1.1; k BUTTON_L1 1.1; k BUTTON_B 1.6
  k DPAD_CENTER 0.25; k BUTTON_B 0.25; k DPAD_CENTER 0.25; k BUTTON_B 1.5; endclip
  shot h2_after_pop 0.5
  # 5. frame timing, Full: busy (nav + categories + window) and idle
  dumpsys gfxinfo $P reset >/dev/null 2>&1
  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do k DPAD_DOWN 0.15; done
  k BUTTON_R2 0.7; k BUTTON_R2 0.7; k BUTTON_L2 0.7; k BUTTON_L2 0.7
  k DPAD_CENTER 1.2; k BUTTON_B 1.2; k DPAD_CENTER 1.2; k BUTTON_B 1.2
  perf full_busy
  sleep 2; dumpsys gfxinfo $P reset >/dev/null 2>&1; sleep 8; perf full_idle
  # 6. Lite
  A --es voltcap_test_perf LITE; sleep 2; dumpsys gfxinfo $P reset >/dev/null 2>&1
  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do k DPAD_DOWN 0.15; done
  k BUTTON_R2 0.7; k BUTTON_L2 0.7; k DPAD_CENTER 1.2; k BUTTON_B 1.2
  perf lite_busy
  clip c5_lite_pop 6; k DPAD_DOWN 0.6; k BUTTON_R2 1.2; k DPAD_CENTER 1.6; k BUTTON_B 1.4; endclip
  A --es voltcap_test_perf FULL --es voltcap_test_category ALL; sleep 2
  # 7. text x1.3, long names, missing art
  A --es voltcap_test_scale "font=1.3"; sleep 2; shot s13_home 1; k DPAD_CENTER 1.6; shot s13_pop 0.5; k BUTTON_B 1.4
  A --es voltcap_test_scale off; sleep 1
  A --es voltcap_test_fixture "names=long,art=none,meta=real"; sleep 2.5; shot s_long_home 1
  k DPAD_CENTER 1.6; shot s_long_pop 0.5; k BUTTON_B 1.4
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  # 8. cold start: the app stopped and started again, straight into the layout
  # (The layout saved in the QA app's own settings for this - the snapshot restore puts the QA app's settings back.)
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_layout NIGHT; sleep 2.5
  am force-stop $P; sleep 1.5; clip c6_cold 7; A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 4; picker; sleep 1.5; endclip
  log "run done fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" ;;
restore)
  A --es voltcap_test_scale off --es voltcap_test_fixture off --es voltcap_test_artfix off --es voltcap_test_perf AUTO; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  am force-stop $P; log "QA app stopped" ;;
esac
