#!/system/bin/sh
# 529j (owner video VID20261113180355, compact screens: "incoming categories suddenly spawn at the edge") - category strips,
# same build before / after: the old instant strip (voltcap_test_chipglide legacy) and the 529j glide (new), each recorded
# and logged frame by frame (VoltcapQA chipglide lines). Isolated QA app ONLY, fixture library, launches blocked, preview on
# (nothing saved), owner QA snapshot saved first and put back. Never wakes / unlocks; keys only while the QA app is in front.
#   sh chipstrip529.sh run TAG ["LAYOUTS"] [SCALE]   default layouts: LIBRARY POCKET HUB LIVE CLASSIC TILES; ~40 s each
#   sh chipstrip529.sh restore TAG
# Grade on the PC: python3 tools/qa/tools/chipglide.py OUT/TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
LAYS=${3:-"LIBRARY POCKET HUB LIVE CLASSIC TILES"}; SC=${4:-off}
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
k() { gate; input keyevent KEYCODE_$1; sleep ${2:-0.45}; }
picker() { grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_B 1.5; log "picker dismissed"; }; }
. /data/local/tmp/rec.sh 2>/dev/null || . "$(dirname "$0")/rec.sh"
case $MODE in
run)
  dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate; picker
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  [ "$SC" != off ] && { A --es voltcap_test_scale "$SC"; sleep 2; }
  for lay in $LAYS; do
    for mode in legacy new; do
      A --es voltcap_test_layout $lay --es voltcap_test_category ALL --es voltcap_test_chipglide $mode; sleep 3; gate; picker
      screencap -p $O/${lay}_${mode}_home.png
      logcat -c
      REC_SEG=9 rec_start $O/${lay}_${mode} 16; sleep 0.8
      for i in 1 2 3 4 5 6 7 8; do k BUTTON_R2; done; sleep 0.6
      for i in 1 2 3 4 5 6 7 8; do k BUTTON_L2; done; sleep 0.6
      k BUTTON_R2 0.12; k BUTTON_R2 0.12; k BUTTON_R2 0.12; k BUTTON_L2 0.8
      rec_stop; sleep 0.4
      logcat -d -s VoltcapQA > $O/${lay}_${mode}.qa.txt
      log "$lay $mode done ($(grep -c chipglide $O/${lay}_${mode}.qa.txt) chipglide lines)"
    done
  done
  A --es voltcap_test_chipglide new; log "run done fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" ;;
restore)
  A --es voltcap_test_chipglide new --es voltcap_test_scale off --es voltcap_test_fixture off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  am force-stop $P; log "QA app stopped" ;;
esac
