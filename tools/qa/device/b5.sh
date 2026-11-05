# Update 482 (B5) — scrolling / layout stability per layout: the selected card's rectangle per frame (QaGeom) and the app's
# focus events while pressing R / L slowly, in a burst and reversed; then two stills of one rest (0.6 s and 3 s after a press)
# to catch late labels, stale art and placeholders. Preview + novideo (nothing saved).  setsid sh b5.sh TAG "LAYOUTS" &
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; TAG=$1; LAYS=$2; OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
pk() { for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 2; done; }
input keyevent KEYCODE_WAKEUP; sleep 1
for L in $LAYS; do
  A --ez voltcap_test_geom true; sleep 1
  A --ez voltcap_test_preview true --es voltcap_test_layout $L; sleep 4; pk
  A --ez voltcap_test_novideo true; A --es voltcap_test_category ALL; sleep 3; pk; sleep 2
  logcat -c; log -p e -t VoltcapQA "MARK layout $L"
  log -p e -t VoltcapQA "MARK seq R R R R R R R L L L L L R L R"
  k DPAD_RIGHT 1; k DPAD_RIGHT 1; k DPAD_RIGHT 1; k DPAD_RIGHT 0.05; k DPAD_RIGHT 0.05; k DPAD_RIGHT 0.05; k DPAD_RIGHT 1.1
  k DPAD_LEFT 1; k DPAD_LEFT 1; k DPAD_LEFT 0.05; k DPAD_LEFT 0.05; k DPAD_LEFT 1.1
  k DPAD_RIGHT 0.05; k DPAD_LEFT 0.05; k DPAD_RIGHT 1.2
  logcat -d -s VoltcapQA > $OUT/${L}_geom.txt
  k DPAD_RIGHT 0.6; screencap -p $OUT/${L}_a.png; sleep 3; screencap -p $OUT/${L}_b.png
  echo "$L done" >> $OUT/log.txt
done
A --ez voltcap_test_geom false; A --ez voltcap_test_novideo false; sleep 1
A --ez voltcap_test_preview false; sleep 1
echo DONE >> $OUT/log.txt
