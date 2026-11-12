# Sprint 527 Flip 2 batch i (QA app only): Phase 1 alignment (overlay + log, then clean) on the start-up stage and the in-app
# picker - 1/2/3/5/8 profiles, first / middle / last, photos + letters, scrolled, x1.0 / x1.3, all 7 looks - and the
# Rollout fix (Confirm -> Home, A A Right), Pixel / Off intros. Stops at the first STOP (gate).
S=/data/local/tmp/ci527.sh; O=/sdcard/pc/ci527
run1() { sh $S "$@"; c=$?; [ $c = 3 ] && { echo "BATCH STOPPED at $2" >> $O/log.txt; exit 3; }; }
mkdir -p $O; echo "batch i start $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
export REC_BITRATE=8000000
run1 geom i01_aur_1          AURORA   1:photo                 off
run1 geom i02_neon_2         NEON     2:photo:active=1        off
run1 geom i03_mid_3_13       MIDNIGHT 3:photo:active=1        font=1.3
run1 geom i04_theme_5_last   THEME    5:photo:active=4        off
run1 geom i05_ign_8_mid      IGNITION 8:photo:active=3        off
run1 geom i06_con_8long13    CONSOLE  8:long:photo:active=7   font=1.3
run1 geom i07_oled_5_short   OLED     5:photo:active=0        off      SHORT 0.75
for L in AURORA CONSOLE NEON OLED THEME MIDNIGHT IGNITION; do run1 geomapp a_$L $L 5:photo:active=2 off; done
run1 geomapp a_AURORA_8_13   AURORA   8:photo:active=7        font=1.3
run1 geomapp a_THEME_3long13 THEME    3:long:photo:active=0   font=1.3
run1 cold i10_confirm_home   FULL  AURORA 5:active=2  off 8  4.4:BUTTON_A
run1 cold i11_rapid_aar      FULL  THEME  5           off 9  4.6:BUTTON_A+BUTTON_A+DPAD_RIGHT
run1 cold i12_pixel          PIXEL AURORA 4           off 9
run1 cold i13_intro_off      OFF   AURORA 4           off 6
sh $S restore
echo "batch i end $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> /sdcard/pc/ci527/log.txt
