# Sprint 527 Flip 2 batch g (QA app only): 3ecd4ed2 - the light from the nearest capsule end (ring left / right / under), x1.3 hint,
# rapid A A Right, skip. Stops at the first STOP (gate).
S=/data/local/tmp/ci527.sh; O=/sdcard/pc/ci527
run1() { sh $S "$@"; c=$?; [ $c = 3 ] && { echo "BATCH STOPPED at $2" >> $O/log.txt; exit 3; }; }
mkdir -p $O; echo "batch g start $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
export REC_BITRATE=8000000
run1 cold g01_full_aur_left      FULL  AURORA   4                off      9
run1 cold g02_full_neon_right    FULL  NEON     5:active=4       off      9
run1 cold g03_full_mid_center    FULL  MIDNIGHT 5:active=2       off      9
run1 cold g04_full_aur_long13    FULL  AURORA   4:long:active=2  font=1.3 9
run1 cold g05_short_theme_left   SHORT THEME    4:active=1       off      7
run1 cold g06_rapid_theme        FULL  THEME    5                off      9  1.6:DPAD_RIGHT+DPAD_RIGHT+DPAD_RIGHT+DPAD_LEFT 3.4:DPAD_RIGHT+DPAD_RIGHT+DPAD_RIGHT 4.6:BUTTON_A+BUTTON_A+DPAD_RIGHT
run1 cold g07_skip_ign           FULL  IGNITION 4                off      7  1.5:DPAD_DOWN
sh $S restore
echo "batch g end $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> /sdcard/pc/ci527/log.txt
