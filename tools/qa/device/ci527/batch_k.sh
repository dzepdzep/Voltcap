# Sprint 527 KONKR batch k (960x640, QA app only): the owner's compact-screen checks - alignment overlay (x1.0 / x1.3, 8 profiles
# scrolled, long names, photos), the hint row, glow, navigation, confirmation, idle; one clean recording; then restore.
S=/data/local/tmp/ci527.sh; O=/sdcard/pc/ci527
run1() { dumpsys power | grep -q "mWakefulness=Awake" || { input keyevent KEYCODE_WAKEUP; sleep 1.5; echo "woke the handheld before $2 (owner authorization 12 Nov)" >> $O/log.txt; }; sh $S "$@"; c=$?; [ $c = 3 ] && { echo "BATCH STOPPED at $2" >> $O/log.txt; exit 3; }; }
mkdir -p $O; echo "batch k start $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
export REC_BITRATE=8000000
run1 geom k01_aur_5        AURORA   5:photo:active=2       off
run1 geom k02_ign_8_13     IGNITION 8:photo:active=5       font=1.3
run1 geom k03_neon_8long   NEON     8:long:photo:active=7  off
run1 geom k04_mid_3long13  MIDNIGHT 3:long:photo:active=0  font=1.3
run1 geomapp ka_THEME      THEME    5:photo:active=2       off
run1 geomapp ka_CONSOLE_13 CONSOLE  8:long:photo:active=4  font=1.3
export REC_SEG=14 REC_BITRATE=10000000
run1 cold k10_final        FULL IGNITION 5:photo:active=2 off 12 4.8:DPAD_RIGHT 6.0:DPAD_LEFT 7.4:BUTTON_A
run1 cold k11_rapid        FULL THEME 5 off 9 4.6:BUTTON_A+BUTTON_A+DPAD_RIGHT
run1 idle k_idle_AURORA AURORA 16
sh $S restore
echo "batch k end $(date +%T)" >> $O/log.txt
