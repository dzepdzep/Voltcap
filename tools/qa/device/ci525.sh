#!/system/bin/sh
# Sprint 525 - Connected Ignition hardware QA on the isolated QA app ONLY (com.shelf.launcher.qa), without run-as (the QA
# app is not debuggable): the intro through `voltcap_test_intro`, the picker look through `voltcap_test_pref picker_look=`.
# Never wakes / unlocks the handheld; a key only while the QA app is in front (else STOP, nothing else sent); never HOME.
#   sh ci525.sh run TAG "INTRO:LOOK ..."   per pair: cold start recorded (rec.sh), launch time, frame stats, a shot at 2 s and
#                                          at the end, the screen mark; then for the first pair: D-pad walk, B (keep), A (pick)
#   sh ci525.sh restore                    intro_style and picker_look back as they were
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || { echo "STOP asleep" >> $O/log.txt; exit 3; }
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && { echo "STOP locked" >> $O/log.txt; exit 3; }
  dumpsys activity activities | grep -m1 topResumedActivity | grep -q "$P/" || { echo "STOP QA app not in front" >> $O/log.txt; exit 3; }; }
k() { gate; input keyevent KEYCODE_$1; sleep ${2:-0.3}; }
. /data/local/tmp/rec.sh
case $MODE in
run)
  rm -rf $O; mkdir -p $O; echo "start $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" > $O/log.txt
  grep -o '"picker_look":"[A-Z]*"' $F/voltcap_test_snapshot.json > $O/look_before.txt 2>/dev/null
  first=1
  for pair in $3; do I=${pair%%:*}; L=${pair##*:}; N=${I}_$L
    A --es voltcap_test_intro $I --es voltcap_test_pref "picker_look=$L"; sleep 1.2
    am force-stop $P; sleep 1
    rec_start $O/$N 10
    am start -W -n $P/com.shelf.launcher.MainActivity 2>/dev/null | grep -E "TotalTime|WaitTime" > $O/$N.launch.txt
    sleep 1.5; screencap -p $O/${N}_2s.png; sleep 5
    dumpsys gfxinfo $P > $O/$N.gfx.txt; screencap -p $O/${N}_end.png
    rec_stop
    echo "$N mark=$(cat $F/voltcap_qa_screen 2>/dev/null) $(tr '\n' ' ' < $O/$N.launch.txt) $(grep -E 'Janky frames:|90th percentile' $O/$N.gfx.txt | head -2 | tr '\n' ' ')" >> $O/log.txt
    if [ $first = 1 ]; then first=0
      grep -q '^PICKER' $F/voltcap_qa_screen || { echo "no picker after the cold start - walk skipped" >> $O/log.txt; continue; }
      dumpsys gfxinfo $P reset > /dev/null; rec_start $O/${N}_walk 12
      k DPAD_RIGHT; k DPAD_RIGHT; k DPAD_LEFT; k DPAD_RIGHT; k DPAD_RIGHT; k DPAD_RIGHT 1.2
      screencap -p $O/${N}_walk.png; k BUTTON_B 2.2; screencap -p $O/${N}_afterB.png
      echo "after B mark=$(cat $F/voltcap_qa_screen)" >> $O/log.txt
      rec_stop; dumpsys gfxinfo $P | grep -E "Janky frames:|90th percentile" >> $O/log.txt
      # A on the picker (a cold start again): picks the profile you're on, the home builds under the stage
      am force-stop $P; sleep 1; rec_start $O/${N}_pick 10; A; sleep 4.5
      grep -q '^PICKER' $F/voltcap_qa_screen && { k BUTTON_A 3; screencap -p $O/${N}_picked.png; }
      echo "after A mark=$(cat $F/voltcap_qa_screen)" >> $O/log.txt; rec_stop
      # A during the intro (skip): one press, straight to the settled picker
      am force-stop $P; sleep 1; rec_start $O/${N}_skip 8; A; sleep 0.8; k BUTTON_A 2.5; screencap -p $O/${N}_skip.png
      echo "skip mark=$(cat $F/voltcap_qa_screen)" >> $O/log.txt; rec_stop
    fi
  done
  echo "end $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt; echo DONE >> $O/log.txt ;;
restore)
  L=$(sed 's/.*:"\([A-Z]*\)".*/\1/' /sdcard/pc/$TAG/look_before.txt 2>/dev/null); [ -z "$L" ] && L=MIDNIGHT
  A --es voltcap_test_intro restore --es voltcap_test_pref "picker_look=$L"; echo "restored intro + picker_look=$L" ;;
esac
