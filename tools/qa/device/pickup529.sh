#!/system/bin/sh
# 529s (owner P3): frame recordings of the physical pickup - A opens the popup (the object leaves its place), B closes it
# (back into the same place). Isolated QA app ONLY (com.shelf.launcher.qa), fixture library, launches blocked, preview on,
# owner snapshot saved / restored. Never wakes the device (an asleep display stops the run), keys only while the QA app has
# the focus.   sh pickup529.sh run TAG SCALE "LAYOUTS" [GAME]  |  sh pickup529.sh restore TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
k() { for w in 1 2 3 4 5 6; do f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) input keyevent $1; return 0;; *=null*) log "ABORT no focus (asleep?)"; exit 2;; esac; sleep 0.5; done
  log "ABORT focus=$f"; exit 2; }
pk() { for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k KEYCODE_BUTTON_B; sleep 1.5; log "picker closed with B"; done; }
case $MODE in
run)
  SC=$3; LAYS=$4; GAME=${5:-Shadow of the Colossus}; rm -rf $O; mkdir -p $O
  dumpsys power | grep -q "mWakefulness=Awake" || { log "STOP asleep - not woken"; echo DONE >> $L; exit 3; }
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && { log "STOP locked"; echo DONE >> $L; exit 3; }
  mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; pk
  A --es voltcap_test_snapshot save; sleep 2
  A --es voltcap_test_artfix "delay=0"; sleep 3
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  [ -f $F/qa_nolaunch ] && touch $O/nolaunch_was_there; touch $F/qa_nolaunch; A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; A --es voltcap_test_perf FULL; sleep 1.5
  log "setup scale=$SC game=$GAME build=$(dumpsys package $P | grep -m1 versionName | tr -d ' ')"
  for LY in $LAYS; do
    dumpsys power | grep -q "mWakefulness=Awake" || { log "STOP asleep before $LY"; break; }
    A --ez voltcap_test_preview true; A --es voltcap_test_layout $LY --es voltcap_test_category ALL; sleep 3.5; pk
    A --es voltcap_test_game "$GAME"; sleep 2.5; pk
    # (Card binder plays on A by default - cards_a_plays - off for the recording; the snapshot restore puts it back.)
    [ $LY = CARDS ] && { A --es voltcap_test_pref "cards_a_plays=false"; sleep 1; log "CARDS: cards_a_plays=false for the run"; }
    # (Cube menu's Classic look starts on the big cube: its first A opens the category's games, not a window - one A first;
    # a window that did open there is closed again with B before the recording.)
    if [ $LY = CUBE ]; then logcat -c; k KEYCODE_BUTTON_A; sleep 1.8
      if logcat -d -s VoltcapQA | grep -q 'pickup layout'; then k KEYCODE_BUTTON_B; sleep 1.8; log "CUBE pre-A opened a window (closed with B)"; else log "CUBE pre-A entered the games"; fi; fi
    screencap -p $O/${LY}_home.png; logcat -c
    screenrecord --bit-rate 12000000 --time-limit 7 $O/${LY}.mp4 & R=$!
    sleep 0.9; echo "$(date +%s.%N | cut -c1-14) A" > $O/${LY}_keys.txt; k KEYCODE_BUTTON_A; sleep 2.6
    screencap -p $O/${LY}_open.png
    echo "$(date +%s.%N | cut -c1-14) B" >> $O/${LY}_keys.txt; k KEYCODE_BUTTON_B; sleep 2.2
    wait $R; screencap -p $O/${LY}_closed.png; logcat -d -s VoltcapQA > $O/${LY}_qa.txt
    log "$LY recorded fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION') window=$(grep -c 'pickup layout' $O/${LY}_qa.txt) $(grep -o 'pickup layout.*' $O/${LY}_qa.txt | head -1)"
  done
  echo DONE >> $L ;;
restore)
  # (529t: a run that stopped before saving the owner's snapshot (asleep) has nothing to put back - restoring then re-applied an
  # older snapshot to a stopped app and reported MISMATCH, pk529t4.)
  [ -d $O/snapbak ] || { log "restore: nothing to restore (the run saved no snapshot)"; exit 0; }
  A --es voltcap_test_scale off --es voltcap_test_fixture off --es voltcap_test_artfix off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  # (v0.6.72 PR gate, test_no_device_script_lifts_the_block: the launch block stays - only the final clean-up with the
  # release build back removes qa_nolaunch, never a QA script.)
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi ;;
esac
