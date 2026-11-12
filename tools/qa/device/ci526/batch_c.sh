# Sprint 526 batch c (Flip 2, QA app only): the shrink repro on the fixed build, and re-recordings of the runs whose recorder
# stalled (c02 / c08 / c09: no frames for 1.3-8.8 s after ~2.7 s while the app kept drawing) - lower bit rate, 6 s segments.
S=/data/local/tmp/ci526.sh; O=/sdcard/pc/ci526
run1() { sh $S "$@"; c=$?; [ $c = 3 ] && { echo "BATCH STOPPED at $2" >> $O/log.txt; exit 3; }; }
echo "batch c start $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
export REC_BITRATE=8000000
run1 shrink x01_shrink_fixed
run1 cold c02r_full_ign_4long13 FULL  IGNITION 4:long:active=2 font=1.3 12 6:DPAD_RIGHT 8.5:BUTTON_A
run1 cold c09r_full_console_4   FULL  CONSOLE  4              off      8
run1 cold c08r_rapid_theme      FULL  THEME    5              off      9  1.6:DPAD_RIGHT+DPAD_RIGHT+DPAD_RIGHT+DPAD_LEFT 3.4:DPAD_RIGHT+DPAD_RIGHT+DPAD_RIGHT 4.6:BUTTON_A+BUTTON_A+DPAD_RIGHT
run1 cold c02s_full_ign_4long13 FULL  IGNITION 4:long:active=2 font=1.3 9
run1 cold c16_short_aur_4long13 SHORT AURORA   4:long         font=1.3 7
sh $S restore
echo "batch c end $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
