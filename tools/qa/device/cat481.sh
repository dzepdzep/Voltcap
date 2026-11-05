# Update 481 (A5) — a very long category name in every layout: still + QaClip dump per layout. Preview / QA hooks only.
#   setsid sh cat481.sh TAG "LAYOUTS" &   → /sdcard/pc/TAG/<L>.png + <L>.json; grade: geomcheck.py --clip (texts with the name)
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; TAG=$1; LAYS=$2; OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
pk() { for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 2; done; }
NAME="Nintendo Entertainment System Classic Mini Collection Ultimate Edition"
input keyevent KEYCODE_WAKEUP; sleep 1
for L in $LAYS; do
  A --ez voltcap_test_preview true --es voltcap_test_layout $L; sleep 4; pk
  A --es voltcap_test_catname "$NAME"; sleep 1
  A --es voltcap_test_category "${CAT:-Xbox360}"; sleep 3; pk; sleep 2
  screencap -p $OUT/$L.png
  rm -f $F/qa_clip/${TAG}_$L.json; A --es voltcap_test_clip ${TAG}_$L; sleep 2.5
  cp $F/qa_clip/${TAG}_$L.json $OUT/$L.json 2>/dev/null || echo "$L no dump" >> $OUT/log.txt
  echo "$L done" >> $OUT/log.txt
done
A --es voltcap_test_catname off; sleep 1
A --ez voltcap_test_preview false; sleep 1
echo DONE >> $OUT/log.txt
