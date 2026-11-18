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
# (681x: the old ERE's [^]] bracket did not match under the device's toybox grep - the TV dump had Play focused and A was
# pressed. One node per line (split on '{'; a node holds no nested object), then fixed-string matches only.)
# (682f) the D-pad is on the "Hide from library" option row (QaClip: a focused node whose label carries it)
onhide() { [ -s $O/$1.json ] && tr -d '\n' < $O/$1.json | tr '{' '\n' | grep -F '"focused":true' | grep -qiF 'Hide from library'; } # (682h: -i, Hub / TV print it in capitals)
playfocused() { [ -s $O/$1.json ] && tr -d '\n' < $O/$1.json | tr '{' '\n' | grep -F '"label":"Play",' | grep -F '"kind":"control",' | grep -qF '"focused":true'; }
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
    # (681z, review 44247782675: the category still did not take - the QA library had no app, the launcher logged "category
    # App is empty here; left as it was" and the window opened on a ROM game. Then the confirmation is NOT TESTED, said so.)
    # (No logcat -c here: the layout's blocked-launch count reads the same buffer.)
    # (682c: the QA library gets an app of its own for this - Settings, never launched, never hidden; taken out after)
    A --es voltcap_test_libapp add:com.android.settings; sleep 2
    e0=$(logcat -d -s VoltcapQA | grep -cF 'category App is empty here'); A --es voltcap_test_category App; sleep 2
    if [ "$(logcat -d -s VoltcapQA | grep -cF 'category App is empty here')" -gt "$e0" ]; then log "$lay: no Android app in the QA library - destructive confirmation NOT TESTED"
    elif openw $lay ${n}_app; then
      k BUTTON_L1 1.2; shot ${n}_app_options
      # (682f, review 44264372593: ten Downs left the list for the "B Back" hint and the A pressed it - back to Info, logged as
      # armed. Now one Down at a time, the dump read after each: A only once "Hide from library" itself has the focus.)
      j=0; found=0
      while [ $j -lt 14 ]; do k DPAD_DOWN 0.5; sleep 0.3; shot ${n}_app_step$j
        if onhide ${TAG}_${n}_app_step$j; then found=1; break; fi
        if playfocused ${TAG}_${n}_app_step$j; then break; fi
        j=$((j + 1)); done
      cp $O/${n}_app_step$j.png $O/${n}_app_options_last.png 2>/dev/null
      if [ $found = 0 ]; then log "$lay: the focus never reached 'Hide from library' ($j Downs) - A NOT pressed (armed confirmation NOT TESTED)"
      else
        k BUTTON_A 1.0; shot ${n}_app_armed
        # (claimed only when the dump shows the confirmation line - text, not the log's hope)
        # (682h: the dumps are kept as $O/${TAG}_<name>.json - review 44272567211 read the wrong name and called a shown
        # confirmation missing)
        if grep -qiE 'again to confirm|cover lifted|guarded switch' $O/${TAG}_${n}_app_armed.json 2>/dev/null; then
          k DPAD_UP 0.9; shot ${n}_app_disarmed
          if grep -qiE 'again to confirm|cover lifted|guarded switch' $O/${TAG}_${n}_app_disarmed.json 2>/dev/null; then log "$lay: FAIL - the confirmation stayed after moving off"
          else log "$lay: destructive row armed (confirmation shown) and left (cancelled; never confirmed)"; fi
        else log "$lay: FAIL - A on 'Hide from library' showed no confirmation"; fi
      fi
      k BUTTON_B 1.2; k BUTTON_B 1.4
    fi
    A --es voltcap_test_category ALL; sleep 1.5
    A --es voltcap_test_libapp remove:com.android.settings; sleep 1.5
    # Motion Off: the walk again, recorded - page changes immediate.
    A --es voltcap_test_game "${MIX:-Shadow of the Colossus}"; sleep 2
    MOTION=OFF; if openw $lay ${n}_off; then REC_SEG=10 rec_start $O/${n}_walk_motionoff 10; sleep 0.6
      k BUTTON_R1 1.0; k BUTTON_R1 1.0; k BUTTON_R1 1.0; k BUTTON_R1 1.2; rec_stop; k BUTTON_B 1.4; fi; MOTION=NORMAL
    log "$lay done (launches: $(launches))"
  done
  log "run done fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')"; echo DONE >> $L ;;
restore)
  A --es voltcap_test_libapp remove:com.android.settings; sleep 1.5
  A --es voltcap_test_pref "motion_level=NORMAL" --es voltcap_test_scale off --es voltcap_test_fixture off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  # (681z, review 44247782675: on a fresh emulator there were no snapshot files to keep - the empty folder's literal glob made
  # cp / cmp fail and the restore logged diff=1. An empty keep folder means there was nothing to put back.)
  if [ -d $O/snapbak ] && [ -n "$(ls $O/snapbak 2>/dev/null)" ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"
  elif [ -d $O/snapbak ]; then log "owner snapshot files back: none existed before the run"; fi
  am force-stop $P; log "QA app stopped" ;;
esac
