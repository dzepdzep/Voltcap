# popmore.sh TAG "LAYOUTS" [RIGHTS] — update 451 More check (preview only, launches blocked): A opens the window (still 1), ▶ x RIGHTS
# (default 3: Play → Details → Back/Inspect → More) (still 2), A on it (still 3: More open, focus on its first action), B (still 4:
# More closed, window still open), B (still 5: window closed). Logs the focused window token after each step.
P=com.shelf.launcher; OUT=/sdcard/pm/$1; rm -rf $OUT; mkdir -p $OUT; R=${3:-3}
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" | tail -1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.7}; }
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 2
for L in $2; do
  A --es voltcap_test_layout $L; sleep 3.5; k SCROLL_LOCK 0.4; H=$(W)
  k BUTTON_A 1.6; screencap -p $OUT/${L}_1.png; W1=$(W)
  i=0; while [ $i -lt $R ]; do k DPAD_RIGHT 0.5; i=$((i+1)); done; screencap -p $OUT/${L}_2.png
  k BUTTON_A 1.0; screencap -p $OUT/${L}_3.png; W3=$(W)
  k BUTTON_B 1.0; screencap -p $OUT/${L}_4.png; W4=$(W)
  k BUTTON_B 1.2; screencap -p $OUT/${L}_5.png; W5=$(W)
  echo "$L home=$H open=$W1 more=$W3 afterB=$W4 afterBB=$W5" >> $OUT/log.txt
  [ "$W5" != "$H" ] && { k BUTTON_B 1; echo "$L extra B" >> $OUT/log.txt; }
done
A --ez voltcap_test_preview false
echo "launch blocked: $(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')" >> $OUT/log.txt; echo DONE >> $OUT/log.txt
