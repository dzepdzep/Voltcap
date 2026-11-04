# catparity.sh TAG N — the owner's own layout: category screen tiles vs L2/R2 stops (N x R2), then back with L2; view state
# saved first and restored + verified after (staging build). Read-only otherwise.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pv/$1; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
A --es voltcap_test_snapshot save; sleep 2; cp $F/voltcap_test_snapshot_view.json $OUT/view_before.json
input keyevent KEYCODE_SCROLL_LOCK; sleep 0.4; logcat -c
input keyevent KEYCODE_BUTTON_B; sleep 2.5; screencap -p $OUT/screen.png; input keyevent KEYCODE_BUTTON_B; sleep 1.5
for i in $(seq 1 $2); do input keyevent KEYCODE_BUTTON_R2; sleep 0.6; done
screencap -p $OUT/after_r2.png
for i in $(seq 1 $2); do input keyevent KEYCODE_BUTTON_L2; sleep 0.6; done
logcat -d -s VoltcapQA | grep -E "category|tiles|stop " > $OUT/log.txt
A --es voltcap_test_snapshot restore; sleep 3; cat $F/voltcap_test_snapshot_verify.txt > $OUT/verify.txt
echo DONE >> $OUT/log.txt
