# popmodes.sh TAG "LAYOUTS" — update 454: every R1 popup style x layout (preview, launches blocked): A opens (still), focus line,
# B closes (window token back to home), no launch. Saves the owner's snapshot first and restores + verifies it at the end
# (voltcap_test_pref writes popup_style_by_layout during the run).
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pv/$1; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" | tail -1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
A --es voltcap_test_snapshot save; sleep 2
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 1
for L in $2; do
  A --es voltcap_test_layout $L; sleep 3.5
  for st in BANNER CLASSIC ROW_REVEAL ROW_OPENS SHARED OWN; do
    A --es voltcap_test_pref "popup_style_by_layout=$L=$st"; sleep 1.5; k SCROLL_LOCK 0.3; H=$(W); logcat -c
    k BUTTON_A 1.6; W1=$(W); screencap -p $OUT/${L}_${st}.png
    k BUTTON_B 1.4; W2=$(W); [ "$W2" != "$H" ] && { k BUTTON_B 1.2; W2b=$(W); }
    LB=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')
    echo "$L $st opened=$([ "$W1" != "$H" ] && echo window || echo inline) closed_by_B=$([ "$W2" = "$H" ] && echo yes || echo "NO${W2b:+(2nd B: $([ "$W2b" = "$H" ] && echo closed || echo open))}") launches=$LB" >> $OUT/log.txt; W2b=
  done
done
A --ez voltcap_test_preview false; sleep 1
A --es voltcap_test_snapshot restore; sleep 3; echo "restore: $(cat $F/voltcap_test_snapshot_verify.txt)" >> $OUT/log.txt; echo DONE >> $OUT/log.txt
