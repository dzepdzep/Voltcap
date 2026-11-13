#!/system/bin/sh
# 529k - the shelf family's own pages (Game shelf, Case stacks, Console stack, Coverflow details) on the handheld. Isolated QA
# app ONLY (com.shelf.launcher.qa), fixture library + achievements, launches blocked, preview on (nothing saved); the owner's QA
# snapshot is kept first and put back by `restore`. Never wakes / unlocks: keys only while the QA app is in front and awake.
#   sh shelfpages529.sh run TAG [SCALE] ["LAYOUTS"]   per layout: A opens; R1 R1 R1 walks Details / Achievements / Options
#                                                     (recorded, a shot + layout dump per page); L1 x3 back; B closes; rapid
#                                                     B A x3 (no launch); then Motion Off: the same R1 walk recorded.
#   sh shelfpages529.sh restore TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
SC=${3:-off}; LAYS=${4:-"SHELF STACKS CONSOLES COVERDETAILS"}
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
k() { gate; input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
shot() { screencap -p $O/$1.png; echo "${TAG}_$1" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.1; }
launches() { logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked'; }
. /data/local/tmp/rec.sh 2>/dev/null
walk() { # name: R1 x3 recorded with a shot per page, then L1 x3
  REC_SEG=9 rec_start $O/$1_r1 9; sleep 0.6
  k BUTTON_R1 1.0; shot $1_details; k BUTTON_R1 1.0; shot $1_trophies; k BUTTON_R1 1.0; shot $1_options
  rec_stop; sleep 0.3
  k BUTTON_L1 0.9; k BUTTON_L1 0.9; k BUTTON_L1 1.0; shot $1_back_main
}
case $MODE in
run)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_B 1.5; log "picker closed with B"; }
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_artfix "delay=0"; sleep 3
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  A --es voltcap_test_game "Shadow of the Colossus"; sleep 2
  log "setup scale=$SC layouts=$LAYS"
  for lay in $LAYS; do
    A --ez voltcap_test_preview true --es voltcap_test_pref "motion_level=NORMAL"; A --es voltcap_test_layout $lay; sleep 3.5; gate
    n=${lay}; logcat -c
    shot ${n}_home; k BUTTON_A 1.9; shot ${n}_main
    walk ${n}
    k BUTTON_B 1.4; shot ${n}_closed
    logcat -c; for i in 1 2 3; do k BUTTON_A 0.35; k BUTTON_B 0.35; done; sleep 1.5
    echo "rapid_reopen_launches=$(launches)" > $O/${n}_rapid.txt; shot ${n}_after_rapid
    # Motion Off (Settings › Motion level, held in preview): page changes are immediate.
    A --es voltcap_test_pref "motion_level=OFF"; sleep 1.2; gate
    k BUTTON_A 1.6; walk ${n}_motionoff; k BUTTON_B 1.4
    A --es voltcap_test_pref "motion_level=NORMAL"; sleep 1
    log "$lay done (launches during the walk: $(launches))"
  done
  log "run done fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')"; echo DONE >> $L ;;
restore)
  A --es voltcap_test_pref "motion_level=NORMAL" --es voltcap_test_scale off --es voltcap_test_fixture off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  am force-stop $P; log "QA app stopped" ;;
esac
