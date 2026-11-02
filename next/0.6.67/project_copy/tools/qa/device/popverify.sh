# popverify.sh TAG "LAYOUTS" — update 452 D156 check per layout (preview only, launches blocked, the owner's popup style):
# A opens (c.png) · More opened as its button does (voltcap_test_winmore, e.png) · B → More closes, window stays (b.png) · B → window
# closes · rapid A B A B (120 ms) → closed, nothing started. PASS line per layout; the app's own "win more=" log lines kept in log.txt.
P=com.shelf.launcher; OUT=/sdcard/pv/$1; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" | tail -1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
picker() { m=/sdcard/Android/data/com.shelf.launcher/files/voltcap_qa_screen; if [ -s $m ] && grep -q "^PICKER $(pidof com.shelf.launcher | cut -d' ' -f1)" $m; then input keyevent KEYCODE_BUTTON_B; sleep 1.5; echo "picker dismissed (mark)"; elif [ ! -s $m ]; then uiautomator dump /sdcard/vqa_ui.xml > /dev/null 2>&1; grep -q "Who.s playing" /sdcard/vqa_ui.xml 2>/dev/null && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; echo "picker dismissed"; }; rm -f /sdcard/vqa_ui.xml; fi; }
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 2
for L in $2; do
  A --es voltcap_test_layout $L; sleep 3.5; picker >> $OUT/log.txt; k SCROLL_LOCK 0.4; H=$(W); logcat -c
  k BUTTON_A 1.6; W1=$(W); [ "$W1" = "$H" ] && { k BUTTON_A 1.6; W1=$(W); }
  screencap -p $OUT/${L}_c.png
  A --es voltcap_test_winmore on; sleep 1.2; screencap -p $OUT/${L}_e.png
  k BUTTON_B 1.0; W2=$(W); screencap -p $OUT/${L}_b.png
  k BUTTON_B 1.2; W3=$(W)
  [ "$W3" != "$H" ] && k BUTTON_B 1.0
  input keyevent KEYCODE_BUTTON_A; sleep 0.12; input keyevent KEYCODE_BUTTON_B; sleep 0.12; input keyevent KEYCODE_BUTTON_A; sleep 0.12; input keyevent KEYCODE_BUTTON_B; sleep 2; W4=$(W)
  [ "$W4" != "$H" ] && { k BUTTON_B 1.2; W4b=$(W); }
  MO=$(logcat -d -s VoltcapQA | grep -o "win more=[a-z/]*" | tr '\n' ' ')
  LB=$(logcat -d -s VoltcapQA | grep -c "LAUNCH blocked")
  ok=PASS; [ "$W1" = "$H" ] && ok="FAIL(no window)"; [ "$W2" != "$W1" ] && ok="FAIL(B closed window, not More)"; [ "$W3" != "$H" ] && ok="$ok FAIL(2nd B)"
  echo "$L $ok rapid_end=$([ "$W4" = "$H" ] && echo closed || echo "open->${W4b:+B}") launches_blocked=$LB | $MO" >> $OUT/log.txt
  W4b=
done
A --ez voltcap_test_nolaunch false; A --ez voltcap_test_preview false
echo DONE >> $OUT/log.txt
