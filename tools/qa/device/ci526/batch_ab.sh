# Sprint 526 Connected Ignition visual QA batch (Flip 2, QA app only). Each line = one ci526.sh run; stops at the first STOP.
S=/data/local/tmp/ci526.sh; O=/sdcard/pc/ci526
run1() { sh $S "$@"; c=$?; [ $c = 3 ] && { echo "BATCH STOPPED at $2" >> $O/log.txt; exit 3; }; }
echo "batch start $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
grep -o '"picker_look":"[A-Z]*"' /sdcard/Android/data/com.shelf.launcher.qa/files/voltcap_test_snapshot.json > $O/look_before.txt 2>/dev/null
case $1 in
a)
run1 cold c01_full_aurora_4    FULL  AURORA   4              off      14 5.5:DPAD_RIGHT 6.5:DPAD_RIGHT 7.5:DPAD_LEFT 9.5:BUTTON_A
run1 cold c02_full_ign_4long13 FULL  IGNITION 4:long:active=2 font=1.3 12 6:DPAD_RIGHT 8.5:BUTTON_A
run1 cold c03_full_aur_4long10 FULL  AURORA   4:long         off      9
run1 cold c04_short_neon_6     SHORT NEON     6              off      9  2.6:DPAD_RIGHT 3.4:DPAD_RIGHT 5:BUTTON_A
run1 cold c05_pixel_console    PIXEL CONSOLE  4              off      8
run1 cold c06_off_oled         OFF   OLED     4              off      6
run1 cold c07_skip_midnight    FULL  MIDNIGHT 3              off      8  1.3:DPAD_DOWN 3.5:DPAD_RIGHT
run1 cold c08_rapid_theme      FULL  THEME    5              off      9  1.6:DPAD_RIGHT+DPAD_RIGHT+DPAD_RIGHT+DPAD_LEFT 3.4:DPAD_RIGHT+DPAD_RIGHT+DPAD_RIGHT 4.6:BUTTON_A+BUTTON_A+DPAD_RIGHT
;;
b)
run1 cold c09_full_console_4   FULL  CONSOLE  4              off      8
run1 cold c10_full_neon_4      FULL  NEON     4              off      8
run1 cold c11_full_oled_4      FULL  OLED     4              off      8
run1 cold c12_full_midnight_4  FULL  MIDNIGHT 4              off      8
run1 cold c13_full_theme_4     FULL  THEME    4              off      8
run1 cold c14_full_aurora_8    FULL  AURORA   8              font=1.3 9
run1 cold c15_short_ign_own    SHORT IGNITION off            off      7  4:BUTTON_B
run1 inapp i01_inapp
run1 homeback h01_homeintent
run1 burst  u01_burst_full     FULL  AURORA   4              off      45
sh $S restore
;;
esac
echo "batch $1 end $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
