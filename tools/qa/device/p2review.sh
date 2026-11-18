#!/system/bin/sh
# P2 (681b, owner 17 Nov: "complete screenshots and short page-transition recordings covering Info, Liner Notes, Tracklist and
# Pressing Notes at x1.0 and x1.3 ... both unlocked and locked achievements, long descriptions and enough items to test
# scrolling ... focused buttons and destructive-option confirmations"): one layout's own pages, reviewed. Isolated QA app ONLY
# (com.shelf.launcher.qa), fixture library + synthetic achievements, launches blocked, preview on (nothing saved); the owner's
# QA snapshot kept and put back by `restore`. Never wakes / unlocks: keys only while the QA app is in front and awake.
#   sh p2review.sh run TAG SCALE "LAYOUTS"   per layout, on a game with earned AND locked achievements (art fixture "achmix"):
#        home, Info (+ the 2nd key focused), the page walk R1 R1 R1 R1 recorded, a shot per page and its focused first row,
#        a track opened (A) and the list scrolled; then the 120-achievement game's Tracklist scrolled far; then an Android app's
#        Options page with a destructive row ARMED (one press - never confirmed) and moved off; Motion Off walk recorded.
#   sh p2review.sh restore TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
SC=${3:-off}; LAYS=${4:-CRATE}
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; echo DONE >> $L; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
k() { gate; input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
dr() { [ -s "$1" ] && [ "$(tail -c 1 "$1" 2>/dev/null)" = "}" ]; }
shot() { rm -f $F/qa_clip/${TAG}_$1.json; screencap -p $O/$1.png; echo "${TAG}_$1" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.1
  i=0; while ! dr $F/qa_clip/${TAG}_$1.json && [ $i -lt 40 ]; do sleep 0.2; i=$((i + 1)); done
  dr $F/qa_clip/${TAG}_$1.json && cp $F/qa_clip/${TAG}_$1.json $O/ || log "dump missing: $1"; }
qawins() { dumpsys window windows | grep -E "Window #|isVisible=" | grep -A1 "Window #.*$P" | grep -c "isVisible=true"; }
# 0 when the dump [1] has the window's Play control focused (a focused node labelled Play), i.e. A would start the game.
playfocused() { [ -s $O/$1.json ] && tr -d '\n' < $O/$1.json | grep -qE '"label":"Play","kind":"control","full":\[[^]]*\],"shown":\[[^]]*\],"focused":true'; }
launches() { logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked'; }
. /data/local/tmp/rec.sh 2>/dev/null
pick() { logcat -d -s VoltcapQA | grep -F "$1" | head -1 | sed 's/.*game=//' | tr -d '\r'; }
openw() { # layout name -> 0 when the game window is open
  A --ez voltcap_test_preview true --es voltcap_test_pref "motion_level=${MOTION:-NORMAL}"; A --es voltcap_test_layout $1; sleep 3.5; gate
  [ "$(qawins)" = 1 ] || { log "$1: PRECONDITION FAILED - $(qawins) QA windows on the home"; screencap -p $O/${2}_precondition.png; return 1; }
  shot ${2}_home; k BUTTON_A 1.9
  [ "$(qawins)" = 2 ] || { log "$1: the game window did not open - NOT TESTED"; screencap -p $O/${2}_precondition.png; k BUTTON_B 1.2; return 1; }; }
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
  logcat -c; A --es voltcap_test_artfix "delay=0"; sleep 3
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  MIX=$(pick "achmix n=10 "); BIG=$(pick "achmix n=120 "); [ -n "$BIG" ] || BIG=$(pick "achfix n=120 ")
  log "setup scale=$SC layouts=$LAYS mixed='$MIX' long='$BIG'"
  [ -n "$MIX" ] || log "no fixture game with both earned and locked achievements - Tracklist sides NOT TESTED"
  for lay in $LAYS; do
    n=$lay; logcat -c
    A --es voltcap_test_game "${MIX:-Shadow of the Colossus}"; sleep 2
    openw $lay $n || continue
    shot ${n}_info; k DPAD_RIGHT 0.9; shot ${n}_info_key2; k DPAD_LEFT 0.9
    REC_SEG=12 rec_start $O/${n}_walk 12; sleep 0.6
    k BUTTON_R1 1.1; k BUTTON_R1 1.1; k BUTTON_R1 1.1; k BUTTON_R1 1.3
    rec_stop; sleep 0.3
    k BUTTON_R1 1.1; shot ${n}_details; k DPAD_UP 0.9; shot ${n}_details_focus
    k BUTTON_R1 1.1; shot ${n}_trophies; k DPAD_DOWN 0.8; k DPAD_DOWN 0.8; shot ${n}_trophies_focus
    k BUTTON_A 1.0; shot ${n}_trophies_open
    for i in 1 2 3 4 5 6 7; do k DPAD_DOWN 0.45; done; sleep 0.6; shot ${n}_trophies_scroll
    k BUTTON_R1 1.1; shot ${n}_options; k DPAD_DOWN 0.8; shot ${n}_options_focus
    k BUTTON_B 1.2; shot ${n}_back_main; k BUTTON_B 1.4; shot ${n}_closed
    if [ -n "$BIG" ]; then
      A --es voltcap_test_game "$BIG"; sleep 2
      if openw $lay ${n}_big; then
        k BUTTON_R1 1.1; k BUTTON_R1 1.1; shot ${n}_big_trophies
        for i in $(seq 1 24); do k DPAD_DOWN 0.3; done; sleep 0.8; shot ${n}_big_trophies_far
        k BUTTON_B 1.2; k BUTTON_B 1.4
      fi
    fi
    # The destructive confirmation: an app in the QA library's App category ("Hide from library" asks first) - one press arms
    # it, moving off cancels, B leaves. Never confirmed.
    A --es voltcap_test_category App; sleep 2
    if openw $lay ${n}_app; then
      k BUTTON_L1 1.2; shot ${n}_app_options
      for i in 1 2 3 4 5 6 7 8 9 10; do k DPAD_DOWN 0.5; done; sleep 0.6; shot ${n}_app_options_last
      # (681v, review 44194071430: the App category did not take, the window was on a ROM game with 9 options, ten Downs left the
      # list for the buttons and this A pressed Play - the QA app blocked the launch. A is pressed only on a focused option row.)
      if playfocused ${TAG}_${n}_app_options_last; then log "$lay: focus left the options for Play - A NOT pressed (armed confirmation NOT TESTED)"
      else
        k BUTTON_A 1.0; shot ${n}_app_armed
        k DPAD_UP 0.9; shot ${n}_app_disarmed; log "$lay: destructive row armed and left (never confirmed)"
      fi
      k BUTTON_B 1.2; k BUTTON_B 1.4
    fi
    A --es voltcap_test_category ALL; sleep 1.5
    # Motion Off: the walk again, recorded - page changes immediate.
    A --es voltcap_test_game "${MIX:-Shadow of the Colossus}"; sleep 2
    MOTION=OFF; if openw $lay ${n}_off; then REC_SEG=10 rec_start $O/${n}_walk_motionoff 10; sleep 0.6
      k BUTTON_R1 1.0; k BUTTON_R1 1.0; k BUTTON_R1 1.0; k BUTTON_R1 1.2; rec_stop; k BUTTON_B 1.4; fi; MOTION=NORMAL
    log "$lay done (launches: $(launches))"
  done
  log "run done fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')"; echo DONE >> $L ;;
restore)
  A --es voltcap_test_pref "motion_level=NORMAL" --es voltcap_test_scale off --es voltcap_test_fixture off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  am force-stop $P; log "QA app stopped" ;;
esac
