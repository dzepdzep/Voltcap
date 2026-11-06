# navcheck.sh TAG "LAYOUTS" "KEYS" — preview each layout, press KEYS (0.45 s apart), log the app's focus lines per key; stills.
# View state saved + restored (verified); preview only; launches blocked.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pv/$1; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
A --es voltcap_test_snapshot save; sleep 2
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 1
for L in $2; do
  A --es voltcap_test_layout $L; sleep 3.5; input keyevent KEYCODE_SCROLL_LOCK; sleep 0.3; logcat -c; screencap -p $OUT/${L}_0.png
  i=0; for K in $3; do i=$((i+1)); echo "KEY $K" >> $OUT/${L}.txt; input keyevent KEYCODE_$K; sleep 0.45
    logcat -d -s VoltcapQA | grep -E " focus |category stop|win |LAUNCH|second" | cut -c19-220 >> $OUT/${L}.txt; logcat -c; done
  screencap -p $OUT/${L}_1.png
done
A --ez voltcap_test_preview false; sleep 1
A --es voltcap_test_snapshot restore; sleep 3; echo "restore: $(cat $F/voltcap_test_snapshot_verify.txt)" > $OUT/log.txt
