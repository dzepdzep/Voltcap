# Sprint 527 Flip 2 batch (QA app only): the refined Connected Ignition. Stops at the first STOP (gate).
S=/data/local/tmp/ci527.sh; O=/sdcard/pc/ci527
run1() { sh $S "$@"; c=$?; [ $c = 3 ] && { echo "BATCH STOPPED at $2" >> $O/log.txt; exit 3; }; }
mkdir -p $O; echo "batch $1 start $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
export REC_BITRATE=8000000
case $1 in
a)
run1 cold f01_full_aurora_4        FULL  AURORA   4                off      13 6:DPAD_RIGHT 7:DPAD_RIGHT 8:DPAD_LEFT 9.6:BUTTON_A
run1 cold f02_full_neon_8_13       FULL  NEON     8:active=2       font=1.3 10 7:DPAD_RIGHT 8:DPAD_RIGHT
run1 cold f03_full_aur_long13_mid  FULL  AURORA   4:long:active=2  font=1.3 9
run1 cold f04_short_mid_6_right    SHORT MIDNIGHT 6:active=4       off      8  4.2:BUTTON_A
run1 cold f05_skip_console         FULL  CONSOLE  4                off      7  1.4:DPAD_DOWN 3.4:DPAD_RIGHT
run1 cold f06_rapid_theme          FULL  THEME    5                off      9  1.6:DPAD_RIGHT+DPAD_RIGHT+DPAD_RIGHT+DPAD_LEFT 3.4:DPAD_RIGHT+DPAD_RIGHT+DPAD_RIGHT 4.6:BUTTON_A+BUTTON_A+DPAD_RIGHT
;;
b)
run1 cold f07_full_oled_3_right    FULL  OLED     3:active=2       off      8
run1 cold f08_full_ign_6_13        FULL  IGNITION 6:active=5       font=1.3 9  6.5:DPAD_LEFT
run1 cold f09_short_aur_1          SHORT AURORA   1                off      6
run1 idle f10_idle_aurora AURORA 16
run1 idle f11_idle_neon NEON 16
run1 idle f12_idle_midnight MIDNIGHT 16
run1 reentry f13_reentry
run1 inapp f14_inapp
sh $S restore
;;
esac
echo "batch $1 end $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
