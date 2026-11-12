# Sprint 527 Flip 2 batch h (QA app only): bf9980b0 final evidence - all 7 looks, 1 / 3 / 5 / 8 profiles, scrolling, long names
# x1.3, SHORT, skip, rapid A A Right after the choice (no game window may open), Confirm -> Home timing, A mid-reveal, idle
# frames for the 4 looks not measured on 3ecd4ed2, interruption + re-entry. Stops at the first STOP (gate).
S=/data/local/tmp/ci527.sh; O=/sdcard/pc/ci527
run1() { sh $S "$@"; c=$?; [ $c = 3 ] && { echo "BATCH STOPPED at $2" >> $O/log.txt; exit 3; }; }
mkdir -p $O; echo "batch h start $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
export REC_BITRATE=8000000
run1 cold h01_full_aur_1         FULL  AURORA   1                off      9
run1 cold h02_full_console_3     FULL  CONSOLE  3:active=2       off      9
run1 cold h03_full_oled_5        FULL  OLED     5:active=4       off      9
run1 cold h04_full_neon_8scroll  FULL  NEON     8:active=6       off      10
run1 cold h05_full_mid_8long13   FULL  MIDNIGHT 8:long:active=3  font=1.3 10
run1 cold h06_short_theme_5      SHORT THEME    5:active=2       off      7
run1 cold h07_skip_ign           FULL  IGNITION 4                off      7  1.5:DPAD_DOWN
run1 cold h08_rapid_aar          FULL  THEME    5                off      9  4.6:BUTTON_A+BUTTON_A+DPAD_RIGHT
run1 cold h09_confirm_home       FULL  AURORA   5:active=2       off      8  4.4:BUTTON_A
run1 cold h10_short_a_midreveal  SHORT NEON     3                off      7  0.8:DPAD_RIGHT+BUTTON_A
for L in CONSOLE OLED THEME IGNITION; do run1 idle h_idle_$L $L 16; done
run1 reentry h11_reentry
sh $S restore
echo "batch h end $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> /sdcard/pc/ci527/log.txt
