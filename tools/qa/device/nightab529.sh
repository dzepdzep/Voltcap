#!/system/bin/sh
# 529j - Night menu (Moonlight) Full vs Lite, measured fairly: the SAME key sequence per segment for each variant, gfxinfo
# reset before each segment. Variants: full (529j bars: no offscreen buffers while they fade with a motion), full_legacy
# (the old per-bar buffers, voltcap_test_nmlayers legacy), lite. Segments: nav (12 x down), cat (R2 R2 L2 L2), pop (A B A B),
# idle (8 s). Each variant runs twice (r1 / r2) in the order full, full_legacy, lite, lite, full_legacy, full - so heat and
# caches don't favour one. Isolated QA app ONLY; preview on (nothing saved); owner QA snapshot kept and put back.
#   sh nightab529.sh run TAG     (~4 min)       sh nightab529.sh restore TAG
# Grade: python3 tools/qa/tools/nightab.py OUT/TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
k() { gate; input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
picker() { grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_B 1.5; log "picker dismissed"; }; }
seg() { dumpsys gfxinfo $P | grep -E "Total frames rendered|Janky frames:|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync|Number Slow UI thread|50th gpu percentile|90th gpu percentile" > $O/$1.txt; log "$1: $(tr '\n' ' ' < $O/$1.txt | cut -c1-160)"; }
reset() { dumpsys gfxinfo $P reset >/dev/null 2>&1; }
variant() { # name perf layers
  A --es voltcap_test_perf $2 --es voltcap_test_nmlayers $3 --es voltcap_test_category ALL; sleep 2.5; gate
  reset; for i in 1 2 3 4 5 6 7 8 9 10 11 12; do k DPAD_DOWN 0.15; done; sleep 0.8; seg ${1}_nav
  reset; k BUTTON_R2 0.7; k BUTTON_R2 0.7; k BUTTON_L2 0.7; k BUTTON_L2 0.9; seg ${1}_cat
  reset; k DPAD_CENTER 1.2; k BUTTON_B 1.2; k DPAD_CENTER 1.2; k BUTTON_B 1.4; seg ${1}_pop
  reset; sleep 8; seg ${1}_idle
  echo "$(cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null) $1" >> $O/temps.txt
}
case $MODE in
run)
  dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate; picker
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_layout NIGHT --es voltcap_test_category ALL; sleep 3; gate; picker
  variant full_r1 FULL new; variant full_legacy_r1 FULL legacy; variant lite_r1 LITE new
  variant lite_r2 LITE new; variant full_legacy_r2 FULL legacy; variant full_r2 FULL new
  A --es voltcap_test_perf AUTO --es voltcap_test_nmlayers new; log "run done fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" ;;
restore)
  A --es voltcap_test_perf AUTO --es voltcap_test_nmlayers new --es voltcap_test_fixture off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  am force-stop $P; log "QA app stopped" ;;
esac
