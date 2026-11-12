# Sprint 527 Flip 2 batch j (QA app only): the owner's final clean demonstration - one normal-speed recording per take, no
# overlay, profile-photo fixture (odd profiles carry a test photo): FULL intro, a move each way, Confirm -> Home. Two takes
# (Connected Ignition look, Aurora look), single-file recordings (REC_SEG 14), 16 Mbit/s.
S=/data/local/tmp/ci527.sh; O=/sdcard/pc/ci527
run1() { dumpsys power | grep -q "mWakefulness=Awake" || { input keyevent KEYCODE_WAKEUP; sleep 1.5; echo "woke the handheld before $2 (owner authorization 12 Nov)" >> $O/log.txt; }; sh $S "$@"; c=$?; [ $c = 3 ] && { echo "BATCH STOPPED at $2" >> $O/log.txt; exit 3; }; }
mkdir -p $O; echo "batch j start $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
export REC_SEG=14 REC_BITRATE=16000000
run1 cold j01_final_ignition FULL IGNITION 5:photo:active=2 off 12 4.8:DPAD_RIGHT 6.0:DPAD_LEFT 7.4:BUTTON_A
run1 cold j02_final_aurora   FULL AURORA   5:photo:active=2 off 12 4.8:DPAD_RIGHT 6.0:DPAD_LEFT 7.4:BUTTON_A
run1 cold j03_final_short    SHORT IGNITION 5:photo:active=2 off 8 2.4:BUTTON_A
sh $S restore
echo "batch j end $(date +%T)" >> $O/log.txt
