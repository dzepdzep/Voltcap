# popaudit461.sh TAG "LAYOUTS" — update 461 popup audit: every layout x R1 popup style (preview, launches blocked).
# Per combo: home before, popup open (settled), B closes (window back to home?), no launch, foreground stays Voltcap.
# Saves the owner's snapshot first and restores + verifies it at the end.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pv/$1; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" | tail -1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
A --es voltcap_test_snapshot save; sleep 2
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 1
for L in $2; do
  input keyevent 224
  A --es voltcap_test_layout $L; sleep 3.5
  for st in OWN BANNER CLASSIC ROW_OPENS ROW_REVEAL SHARED; do
    input keyevent 224
    A --es voltcap_test_pref "popup_style_by_layout=$L=$st"; sleep 1.5; k SCROLL_LOCK 0.3; H=$(W); logcat -c
    k BUTTON_A 1.7; W1=$(W); screencap -p $OUT/${L}_${st}.png
    k BUTTON_B 1.4; W2=$(W); [ "$W2" != "$H" ] && { k BUTTON_B 1.2; W2b=$(W); }
    screencap -p $OUT/${L}_${st}_after.png
    LB=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')
    CR=$(logcat -d -b crash | grep -c "com.shelf.launcher")
    fg=$(dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -c com.shelf.launcher)
    echo "$L $st opened=$([ "$W1" != "$H" ] && echo window || echo inline) closed_by_B=$([ "$W2" = "$H" ] && echo yes || echo "NO${W2b:+(2nd B: $([ "$W2b" = "$H" ] && echo closed || echo open))}") launches=$LB crash=$CR voltcap_front=$fg" >> $OUT/log.txt; W2b=
    [ "$fg" = 0 ] && { echo "STOP not Voltcap in front" >> $OUT/log.txt; A; sleep 3; }
  done
done
A --ez voltcap_test_nolaunch false; A --ez voltcap_test_preview false; sleep 1
A --es voltcap_test_snapshot restore; sleep 3; echo "restore: $(cat $F/voltcap_test_snapshot_verify.txt)" >> $OUT/log.txt; echo DONE >> $OUT/log.txt
