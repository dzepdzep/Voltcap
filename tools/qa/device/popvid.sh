# Native popup motion recording (owner, 12 Nov) - QA app only. One layout per call: the layout as a preview, the run's game,
# launches blocked; records A (open), R1 x3 (pages forward), L1 x3 (back), B (close), then fast A B A B, then B.
# Every key only while the QA app is in front, awake and unlocked, with its picker mark not PICKER; else it stops (no key sent).
# usage: sh popvid.sh LAYOUT "GAME" [SCALE]     SCALE e.g. 1.3 (the QA app's own text scale, not the system's)
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pc/popvid; mkdir -p $OUT; L=$1; G=$2; S=${3:-1.0}
top() { dumpsys activity activities 2>/dev/null | grep -m1 -E "topResumedActivity|mResumedActivity"; }
gate() { dumpsys power 2>/dev/null | grep -q "mWakefulness=Awake" || { echo "STOP asleep"; exit 3; }
  dumpsys window 2>/dev/null | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && { echo "STOP locked"; exit 3; }
  top | grep -q "$P/" || { echo "STOP QA app not in front: $(top)"; exit 3; }
  grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { echo "STOP picker up"; exit 5; }; return 0; }
k() { gate; input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" > /dev/null 2>&1; }
gate; touch $F/qa_nolaunch
A --ez voltcap_test_preview true --ez voltcap_test_nolaunch true --es voltcap_test_scale "font=$S"; sleep 1
A --es voltcap_test_layout $L; sleep 3; A --es voltcap_test_game "$G"; sleep 2; gate
N=${L}_x$S; logcat -c
. "$(dirname "$0")/rec.sh" 2>/dev/null || . /data/local/tmp/rec.sh
rec_start $OUT/$N 24  # (Sprint 525: segments + an early-stop log - tools/qa/tools/recheck.py)
k BUTTON_A 1.6; k BUTTON_R1 1.2; k BUTTON_R1 1.2; k BUTTON_R1 1.2; k BUTTON_L1 1.0; k BUTTON_L1 1.0; k BUTTON_L1 1.2
k BUTTON_B 1.4
k BUTTON_A 0.15; k BUTTON_B 0.15; k BUTTON_A 0.15; k BUTTON_B 1.5
dumpsys window 2>/dev/null | grep -q mCurrentFocus.*$P || true
rec_stop; sleep 0.5
echo "$N launches=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')" | tee $OUT/$N.txt
logcat -d -s VoltcapQA | grep -E "LAUNCH|winkey|focus" | tail -40 >> $OUT/$N.txt
A --ez voltcap_test_preview false --es voltcap_test_scale off
