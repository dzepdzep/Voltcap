# media.sh TAG "LAYOUTS" "P:KEYS" — per console category (safe category hook) then each layout preview: settled still + one ▶ still.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pv/$1; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
A --es voltcap_test_snapshot save; sleep 2; A --ez voltcap_test_nolaunch true
for C in $3; do
  for L in $2; do
    A --ez voltcap_test_preview false; sleep 1
    A --es voltcap_test_category $C; sleep 2.5
    A --ez voltcap_test_preview true; A --es voltcap_test_layout $L; sleep 4
    n=$(echo $C | tr ':' '_'); screencap -p $OUT/${n}_${L}_0.png
    input keyevent KEYCODE_DPAD_RIGHT; sleep 1.5; screencap -p $OUT/${n}_${L}_1.png
  done
done
A --ez voltcap_test_preview false; A --ez voltcap_test_nolaunch false; sleep 1
A --es voltcap_test_snapshot restore; sleep 3; echo "restore: $(cat $F/voltcap_test_snapshot_verify.txt)" > $OUT/log.txt
