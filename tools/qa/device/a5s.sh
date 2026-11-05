# Update 482 (A5 rest) — stale headings / counts after L2 / R2: per layout, the texts on screen after each R2 step (QaClip
# dump) against the same category opened fresh (voltcap_test_category). Preview only.  setsid sh a5s.sh TAG "LAYOUTS" &
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; TAG=$1; LAYS=$2; OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
pk() { for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 2; done; }
dump() { rm -f $F/qa_clip/${TAG}_$1.json; A --es voltcap_test_clip ${TAG}_$1; sleep 2.5; cp $F/qa_clip/${TAG}_$1.json $OUT/$1.json 2>/dev/null || echo "$1 no dump" >> $OUT/log.txt; }
input keyevent KEYCODE_WAKEUP; sleep 1
A --es voltcap_test_snapshot save; sleep 1
for L in $LAYS; do
  A --es voltcap_test_pref "cat_layout_default=$L"; sleep 0.5
  A --ez voltcap_test_preview true --es voltcap_test_layout $L; sleep 4; pk
  A --es voltcap_test_category ALL; sleep 3; pk; sleep 1
  for n in 1 2 3; do
    logcat -c; k BUTTON_R2 2.2; pk
    c=$(logcat -d -s VoltcapQA | grep -o ' focus [0-9-]* [^ ]*' | tail -1 | awk '{print $3}')
    echo "$L step$n $c" >> $OUT/log.txt; dump ${L}_s$n
    screencap -p $OUT/${L}_s$n.png
  done
  for n in 1 2 3; do
    c=$(grep "^$L step$n " $OUT/log.txt | awk '{print $3}'); [ -z "$c" ] && continue
    id=${c%%|*}; [ "$id" = null ] && id="S:${c##*|}"; [ "$id" = "S:null" ] && id=ALL
    A --es voltcap_test_category "$id"; sleep 3; pk; sleep 1; dump ${L}_f$n
  done
  echo "$L done" >> $OUT/log.txt
done
A --ez voltcap_test_preview false; sleep 1
A --es voltcap_test_snapshot restore; sleep 2
echo DONE >> $OUT/log.txt
