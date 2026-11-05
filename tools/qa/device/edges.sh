# VoltCap QA (update 479, B1) — one still per home layout for the edge / unused-space audit. Run detached:
#   setsid sh edges.sh TAG "CLASSIC TILES …" &   → /sdcard/pc/TAG/<LAYOUT>.png + log.txt, DONE at the end
# Preview only (voltcap_test_preview: nothing saved); no navigation, no game started. The caller saves / restores the
# owner's settings (voltcap_test_snapshot) around it. Grade on the host: python3 tools/qa/tools/edgeaudit.py <dir>
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; TAG=$1; LAYS=$2
OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.7}; }
for L in $LAYS; do
  A --ez voltcap_test_preview true --es voltcap_test_layout $L; sleep 4
  A --es voltcap_test_category ALL; sleep 3
  pid=$(pidof $P); for i in 1 2 3 4 5 6 7 8; do grep -q " $pid\$" $F/voltcap_qa_screen 2>/dev/null && break; sleep 0.8; done
  for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 1.5; echo "$L: picker closed with B" >> $OUT/log.txt; done
  grep -q '^HOME' $F/voltcap_qa_screen 2>/dev/null || { echo "$L SKIPPED $(cut -d' ' -f1 $F/voltcap_qa_screen)" >> $OUT/log.txt; continue; }
  sleep 4   # past the rollout and the art fading in
  screencap -p $OUT/$L.png
  echo "$L done" >> $OUT/log.txt
done
echo DONE >> $OUT/log.txt
