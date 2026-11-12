#!/system/bin/sh
# Sprint 527 - category-screen look audit (owner: audit the 17 existing looks before any redesign), isolated QA app ONLY.
# Never wakes / unlocks; keys only while the QA app is in front (else STOP). Preview on (nothing saved) and the settings snapshot
# around it; the owner's QA snapshot files copied aside first and put back byte-for-byte.
#   sh cat527.sh looks TAG [SCALE]   each of the 17 looks: open, settled shot, a move (mid-motion + settled), a short recording
#   sh cat527.sh realart TAG [SCALE] [CAT]  the 17 looks with real artwork (shipped console photos, the QA app's covers)
#   sh cat527.sh follow TAG          Follow home layout + the themed pickers: the look each home layout opens
#   sh cat527.sh pills TAG           the Tile home category pill bar: x1.0 / x1.3, start / middle / end of the list
#   sh cat527.sh restore TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
k() { gate; input keyevent $(echo "$1" | tr '+' '\n' | sed 's/^/KEYCODE_/' | tr '\n' ' '); }
picker() { grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_B; sleep 1.5; log "picker dismissed"; }; }
setup() {
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  A --ez voltcap_test_nolaunch true; sleep 3; picker
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_fixture "names=real,art=${ART:-portrait},meta=none"; sleep 1
  A --es voltcap_test_layout TILES --es voltcap_test_category ${CAT:-ALL}; sleep 3; picker
  A --es voltcap_test_pref "pb_follow_home=false"; sleep 0.5
}
case $MODE in
looks)
  setup; SC=${3:-off}; A --es voltcap_test_scale "$SC"; sleep 1
  . /data/local/tmp/rec.sh; REC_SEG=6; export REC_BITRATE=8000000
  for V in TILES SLIDES WALL GRID STAGE BLADES DRAWERS WORLDS ARCHIVE CHANNEL GENERATIONS CONSTELLATION VAULT DASHBOARD WIICHAN CINEMA ARCTIC; do
    A --es voltcap_test_pref "pb_view=$V"; sleep 0.6
    A --es voltcap_test_catscreen open; sleep 2.8; screencap -p $O/${V}_open.png
    rec_start $O/${V}_rec 4; sleep 0.4; k DPAD_RIGHT; sleep 0.12; screencap -p $O/${V}_moving.png; sleep 1.0; k DPAD_RIGHT; sleep 1.6; rec_stop
    screencap -p $O/${V}_moved.png
    A --es voltcap_test_catscreen close; sleep 1.2; log "look $V"
  done
  A --es voltcap_test_scale off ;;
realart)
  # Owner: the audit again with real artwork - the console photos Voltcap ships (assets/console_hw), the QA app's own covers
  # (fixture art=real: nothing copied from the real Voltcap), opened on a console category; a move to the next console.
  ART=real; CAT=${4:-N64}; setup; SC=${3:-off}; A --es voltcap_test_scale "$SC"; sleep 1
  for V in TILES SLIDES WALL GRID STAGE BLADES DRAWERS WORLDS ARCHIVE CHANNEL GENERATIONS CONSTELLATION VAULT DASHBOARD WIICHAN CINEMA ARCTIC; do
    A --es voltcap_test_pref "pb_view=$V"; sleep 0.6
    A --es voltcap_test_catscreen open; sleep 3; screencap -p $O/R_${V}_open.png
    k DPAD_RIGHT; sleep 1.6; screencap -p $O/R_${V}_next.png
    A --es voltcap_test_catscreen close; sleep 1.2; log "realart $V"
  done
  A --es voltcap_test_scale off ;;
follow)
  setup; A --es voltcap_test_pref "pb_follow_home=true"; sleep 0.5
  for H in SHELF BLADES BOX POCKET LIBRARY TV CHAN CINEMA ARCTIC TIMELINE ORBIT MEMCARD GREENCORE; do
    A --es voltcap_test_layout $H; sleep 2.5; picker
    A --es voltcap_test_catscreen open; sleep 2.8; screencap -p $O/follow_${H}.png
    A --es voltcap_test_catscreen close; sleep 1.2; k BUTTON_B; sleep 0.8; log "follow $H"
  done
  A --es voltcap_test_pref "pb_follow_home=false"; A --es voltcap_test_layout TILES; sleep 2 ;;
pills)
  setup
  for SC in off font=1.3; do A --es voltcap_test_scale "$SC"; sleep 1.5
    A --es voltcap_test_category ALL; sleep 2; screencap -p $O/pills_${SC}_start.png
    for i in 1 2 3 4 5 6; do k BUTTON_R2; sleep 0.5; done; sleep 1.5; screencap -p $O/pills_${SC}_mid.png
    for i in 1 2 3 4 5 6 7 8 9 10 11 12; do k BUTTON_R2; sleep 0.45; done; sleep 1.5; screencap -p $O/pills_${SC}_end.png
  done
  A --es voltcap_test_scale off; A --es voltcap_test_category ALL ;;
restore)
  A --es voltcap_test_catscreen close --es voltcap_test_scale off; sleep 1
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  log "fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" ;;
esac
