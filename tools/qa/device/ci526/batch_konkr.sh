# Sprint 526 KONKR (GT78, 960x640) small-screen pass - QA app only; stops at the first STOP (gate) or when no picker shows.
S=/data/local/tmp/ci526.sh; O=/sdcard/pc/ci526
run1() { sh $S "$@"; c=$?; [ $c = 3 ] && { echo "BATCH STOPPED at $2" >> $O/log.txt; exit 3; }; }
mkdir -p $O; echo "batch k start $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 -E 'lastUpdateTime' | tr -d ' ')" >> $O/log.txt
run1 cold k01_full_aur_4long10  FULL  AURORA   4:long          off      9
grep -q "^k01.*mark=PICKER" $O/log.txt || { echo "no start-up picker on KONKR - stopped" >> $O/log.txt; sh $S restore; exit 4; }
run1 cold k02_full_aur_4long13  FULL  AURORA   4:long          font=1.3 9
run1 cold k03_full_ign_8_13     FULL  IGNITION 8               font=1.3 9
run1 cold k04_short_neon_6      SHORT NEON     6               off      7  3:DPAD_RIGHT
run1 cold k05_full_console_4    FULL  CONSOLE  4:long:active=1 off      10 6:DPAD_RIGHT 7.5:BUTTON_A
run1 cold k06_full_midnight_3   FULL  MIDNIGHT 3               font=1.3 8
sh $S restore
echo "batch k end $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
