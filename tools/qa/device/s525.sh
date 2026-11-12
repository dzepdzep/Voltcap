#!/system/bin/sh
# Sprint 525 hardware QA - the isolated QA app ONLY (com.shelf.launcher.qa), the QA fixture library (GB 1 game, N64 2,
# GC 3, PS1 3). Never wakes or unlocks the handheld; every key only while a QA app window has the focus (else it stops,
# the QA app is stopped and nothing else is pressed). Preview only (nothing saved by the layouts), launches blocked.
#   sh s525.sh fill TAG     - Endless once / Fill the row x the Endless layouts: shots at the start, after going round
#                             forward and back; the QA app's saved Fill keys before / after (run-as, read only)
#   sh s525.sh rec TAG LY   - screenrecord timeline: is the recorder still running, every 0.5 s, around A (popup) / B
#   sh s525.sh spine TAG N  - the Game shelf, fast left / right N times with the oldspine trace on (frames recorded)
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
stop() { echo "ABORT $1" >> $L; am force-stop $P; echo DONE >> $L; exit 2; }
fg() { f=$(dumpsys window | grep mCurrentFocus | tail -1)
  case "$f" in *Application*|*=null*) stop "focus=$f";; *com.shelf.launcher.qa*) return 0;; *) stop "focus=$f";; esac; }
gate() { dumpsys power 2>/dev/null | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window 2>/dev/null | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked; fg; }
k() { fg; input keyevent KEYCODE_$1; sleep ${2:-0.5}; }
pk() { grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_A 1.5; }; return 0; }
prefs() { run-as $P cat shared_prefs/voltcap_features.xml 2>/dev/null | grep -E 'endless_fill' | tr -d ' ' | tr '\n' ' '; }
gate; echo "start $(date +%T) mode=$MODE real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $L
touch $F/qa_nolaunch
# (the fixture library once - asking for it again rescans, and a category picked during the scan is undone as empty)
[ -n "$FIXTURE" ] && { am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true >/dev/null 2>&1; sleep 5; }
case $MODE in
fill)
  echo "prefs before: $(prefs)" >> $L
  A --ez voltcap_test_nolaunch true; sleep 3; pk
  # the old QA snapshot (saved before 525: endless_fill=true under the old key) put back: Fill must stay off
  A --es voltcap_test_snapshot restore; sleep 3; pk
  echo "after old snapshot restore: $(prefs)" >> $L
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 2; A --es voltcap_test_pref "loop_row3=true"; sleep 1
  for FILL in false true; do
    A --es voltcap_test_pref "endless_fill_v2=$FILL"; sleep 1
    for LY in NIGHT AIRY ARTLIB TILES CUBE STACKS CASEFLOW COVERDETAILS; do
      case $LY in NIGHT|ARTLIB) FW=DPAD_DOWN; BK=DPAD_UP;; *) FW=DPAD_RIGHT; BK=DPAD_LEFT;; esac
      for CAT in GameCube N64 GB; do
        N=${LY}_${CAT}_f$FILL
        A --ez voltcap_test_preview true --es voltcap_test_layout $LY --es voltcap_test_category "$CAT"; sleep 2.6; pk
        screencap -p $O/${N}_0.png
        for i in 1 2 3 4; do k $FW 0.45; done; sleep 0.9; screencap -p $O/${N}_fw.png
        for i in 1 2 3 4 5; do k $BK 0.09; done; sleep 1.1; screencap -p $O/${N}_bk.png
        echo "$N" >> $L
      done
    done
  done
  echo "prefs in the run: $(prefs)" >> $L
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  echo "prefs restored: $(prefs)" >> $L ;;
rec)
  LY=${3:-STORAGE}
  A --ez voltcap_test_nolaunch true --ez voltcap_test_preview true --es voltcap_test_layout $LY --es voltcap_test_category GC; sleep 3; pk
  logcat -c
  screenrecord --bit-rate 12000000 --time-limit 12 $O/rec_$LY.mp4 > $O/rec_out.txt 2>&1 & R=$!
  t=0; while [ $t -lt 22 ]; do
    case $t in 4) k BUTTON_A 0.1;; 12) k BUTTON_B 0.1;; esac
    kill -0 $R 2>/dev/null && s=run || s=gone; echo "t=$((t*5))00ms rec=$s" >> $L; sleep 0.5; t=$((t+1)); done
  wait $R; echo "exit=$?" >> $L
  logcat -d 2>/dev/null | grep -iE "screenrecord|ScreenRecord|MediaCodec.*error|DisplayDevice|SurfaceFlinger.*(mode|refresh)|display mode" | tail -40 > $O/rec_logcat.txt
  dumpsys display 2>/dev/null | grep -E "mActiveSfDisplayMode|mDefaultModeId|mActiveModeId" | head -5 >> $L
  A --ez voltcap_test_preview false ;;
spine)
  NN=${3:-12}
  A --ez voltcap_test_nolaunch true --ez voltcap_test_preview true --es voltcap_test_layout SHELF --es voltcap_test_category ALL; sleep 3.5; pk
  logcat -c
  . /data/local/tmp/rec.sh; REC_BITRATE=16000000 rec_start $O/spine 22
  i=0; while [ $i -lt $NN ]; do k DPAD_RIGHT 0.12; i=$((i+1)); done; sleep 0.8
  i=0; while [ $i -lt $NN ]; do k DPAD_LEFT 0.12; i=$((i+1)); done; sleep 0.8
  i=0; while [ $i -lt 6 ]; do k DPAD_RIGHT 0.7; i=$((i+1)); done; sleep 1
  rec_stop
  logcat -d -s VoltcapQA 2>/dev/null | grep -E "oldspine|spine " > $O/spine_trace.txt
  dumpsys gfxinfo $P | grep -E "Janky|50th|90th|99th" >> $L
  A --ez voltcap_test_preview false ;;
esac
echo "end $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $L; echo DONE >> $L
