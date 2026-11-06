# rapid.sh TAG "LAYOUTS" N GAP — rapid A B A B (GAP s) N times per layout, preview + launches blocked; end state per try
P=com.shelf.launcher; OUT=/sdcard/pv/$1; rm -rf $OUT; mkdir -p $OUT; G=${4:-0.12}
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" | tail -1; }
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 2
for L in $2; do
  A --es voltcap_test_layout $L; sleep 3.5; input keyevent KEYCODE_SCROLL_LOCK; sleep 0.4; H=$(W); logcat -c; r=""; LB=0
  for i in $(seq 1 $3); do
    input keyevent KEYCODE_BUTTON_A; sleep $G; input keyevent KEYCODE_BUTTON_B; sleep $G; input keyevent KEYCODE_BUTTON_A; sleep $G; input keyevent KEYCODE_BUTTON_B; sleep 2.2
    if [ "$(W)" = "$H" ]; then r="$r C"; LB=$((LB+$(logcat -d -s VoltcapQA | grep -c "LAUNCH blocked"))); logcat -c; else r="$r O"; screencap -p $OUT/${L}_$i.png; logcat -d -s VoltcapQA > $OUT/${L}_$i.log; input keyevent KEYCODE_BUTTON_B; sleep 1.5; [ "$(W)" != "$H" ] && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; }; fi
  done
  LB=$((LB+$(logcat -d -s VoltcapQA | grep -c "LAUNCH blocked")))
  echo "$L$r launches_blocked=$LB" >> $OUT/log.txt
done
A --ez voltcap_test_preview false; echo DONE >> $OUT/log.txt
