# popstyles.sh TAG LAYOUT — update 452: the other popup styles still open / close with the More change (preview, launches blocked).
# Changes the saved popup_style_by_layout for the run: ALWAYS run viewstate.sh save before and restore after (restores Features too).
P=com.shelf.launcher; OUT=/sdcard/pst/$1; rm -rf $OUT; mkdir -p $OUT; L=$2
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" | tail -1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 2
A --es voltcap_test_layout $L; sleep 3.5
for st in BANNER CLASSIC ROW_REVEAL ROW_OPENS SHARED OWN; do
  A --es voltcap_test_pref "popup_style_by_layout=$L=$st"; sleep 2; k SCROLL_LOCK 0.4; H=$(W); logcat -c
  k BUTTON_A 1.6; screencap -p $OUT/${st}_open.png
  k BUTTON_B 1.4; screencap -p $OUT/${st}_closed.png; W2=$(W)
  echo "$st open_token_changed=$([ "$(W)" = "$H" ] && echo n/a || echo yes) closed=$([ "$W2" = "$H" ] && echo yes || echo NO) launches=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')" >> $OUT/log.txt
  [ "$W2" != "$H" ] && k BUTTON_B 1
done
A --ez voltcap_test_nolaunch false; A --ez voltcap_test_preview false; echo DONE >> $OUT/log.txt
