# Sprint 526 batch d (Flip 2, QA app only): 6a6a9913 - rapid A, A, Right after the leave guard; the shrink back to the active profile.
S=/data/local/tmp/ci526.sh; O=/sdcard/pc/ci526
run1() { sh $S "$@"; c=$?; [ $c = 3 ] && { echo "BATCH STOPPED at $2" >> $O/log.txt; exit 3; }; }
echo "batch d start $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
export REC_BITRATE=8000000
run1 cold c08v_rapid_theme FULL THEME 5 off 9 1.6:DPAD_RIGHT+DPAD_RIGHT+DPAD_RIGHT+DPAD_LEFT 3.4:DPAD_RIGHT+DPAD_RIGHT+DPAD_RIGHT 4.6:BUTTON_A+BUTTON_A+DPAD_RIGHT
run1 shrink x02_shrink_active
sh $S restore
echo "batch d end $(date +%T) real=$(dumpsys package com.shelf.launcher | grep -m1 lastUpdateTime | tr -d ' ')" >> $O/log.txt
