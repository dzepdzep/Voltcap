# Sprint 528 Flip 2 / KONKR batch a (QA app only): Connected Ignition with photographic avatars (fictional generated portraits the
# QA pushed into the QA app's own files/qa_avatars - never anyone's private photo), normal speed, no overlay; then the alignment
# overlay with the same photos (crop, centring, rings, halo) on two looks and the in-app picker at x1.3; restore.
S=/data/local/tmp/ci528.sh; O=/sdcard/pc/ci528
run1() { dumpsys power | grep -q "mWakefulness=Awake" || { input keyevent KEYCODE_WAKEUP; sleep 1.5; echo "woke the handheld before $2" >> $O/log.txt; }; sh $S "$@"; c=$?; [ $c = 3 ] && { echo "BATCH STOPPED at $2" >> $O/log.txt; exit 3; }; }
mkdir -p $O; echo "batch a start $(date +%T) serial=$(getprop ro.serialno) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
echo "avatars: $(ls /sdcard/Android/data/com.shelf.launcher.qa/files/qa_avatars | tr '\n' ' ')" >> $O/log.txt
export REC_SEG=14 REC_BITRATE=16000000
run1 cold a01_photo_ignition FULL IGNITION 6:realphoto:active=2 off 12 4.8:DPAD_RIGHT 6.0:DPAD_LEFT 7.4:BUTTON_A
run1 cold a02_photo_aurora   FULL AURORA   6:realphoto:active=2 off 12 4.8:DPAD_RIGHT 6.0:DPAD_LEFT 7.4:BUTTON_A
run1 cold a03_photo_scroll8  FULL IGNITION 8:realphoto:active=1 font=1.3 13 4.6:DPAD_RIGHT 5.2:DPAD_RIGHT 5.8:DPAD_RIGHT 6.4:DPAD_RIGHT 7.0:DPAD_RIGHT 7.6:DPAD_RIGHT 8.6:BUTTON_A
export REC_SEG=6
run1 geom a04_geom_ignition IGNITION 6:realphoto:active=2 off
run1 geom a05_geom_console  CONSOLE  8:realphoto:long:active=3 font=1.3
run1 geomapp a06_app_theme  THEME    5:realphoto:active=1 font=1.3
sh $S restore
echo "batch a end $(date +%T)" >> $O/log.txt
