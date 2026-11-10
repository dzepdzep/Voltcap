#!/system/bin/sh
# Batch 523 items 1 / 2 / 4: frame recordings of the case coming out of / going back into Game shelf (spines) and Coverflow
# details, and Details kept in the window (Row opens / Row reveal / Shared). Isolated QA app ONLY, fixture library, preview,
# settings snapshotted / restored.  sh motion523.sh TAG [det]   (det: the Details part only)
# (In the popup A is DPAD_CENTER: an injected BUTTON_A has no confirm fallback on the virtual keyboard, so it clicked nothing.)
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true "$@" >/dev/null 2>&1; }
# (Batch 523: every key only while the QA app's own window has focus - after a crash the "has stopped" dialog's buttons led
# into Settings' App info, where the next keys reached the QA app's Uninstall prompt. Anything else stops the run.)
k() { for w in 1 2 3 4 5 6 7 8 9 10; do f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *Application*) ;; *com.shelf.launcher.qa*) break;; *=null*) input keyevent KEYCODE_WAKEUP;; esac; sleep 0.5; done
  case "$f" in *Application*) echo "ABORT focus=$f" >> $L; am force-stop com.shelf.launcher.qa; exit 2;; *com.shelf.launcher.qa*) input keyevent $1;; *) echo "ABORT focus=$f" >> $L; am force-stop com.shelf.launcher.qa; exit 2;; esac; }
pk() { grep -q '^CRASH' $F/voltcap_qa_screen 2>/dev/null && { k KEYCODE_BUTTON_B; sleep 2; }; grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { k KEYCODE_BUTTON_B; sleep 1.5; }; }
input keyevent KEYCODE_WAKEUP; A --ez voltcap_test_nolaunch true; sleep 4; pk
A --es voltcap_test_snapshot save; sleep 2; A --ez voltcap_test_preview true; sleep 2
LYS="SHELF COVERDETAILS"; [ "$2" = det ] && LYS=""
for LY in $LYS; do
  # (Performance FULL for this session only: on AUTO a handheld that saw slow frames turns Lite on, and Lite swaps the
  # big case at once - the flight being recorded isn't drawn.)
  A --ez voltcap_test_preview true; A --es voltcap_test_perf FULL; sleep 1.5; A --es voltcap_test_layout $LY --es voltcap_test_category ALL; sleep 4; pk
  [ $LY = SHELF ] && A --es voltcap_test_pref "shelf_stand=SPINES"; sleep 1
  screenrecord --time-limit 7 --bit-rate 10000000 $O/${LY}.mp4 &
  R=$!; sleep 0.8
  for i in 1 2 3; do k KEYCODE_DPAD_RIGHT; sleep 1.3; done; k KEYCODE_DPAD_LEFT; sleep 1.3
  wait $R; screencap -p $O/${LY}_end.png; echo "$LY" >> $L
done
for ST in ROW_OPENS ROW_REVEAL SHARED; do
  A --ez voltcap_test_preview true; A --es voltcap_test_layout CLASSIC --es voltcap_test_category ALL; sleep 4; pk
  A --es voltcap_test_pref "popup_style_by_layout=CLASSIC=$ST"; sleep 1.2
  k KEYCODE_BUTTON_A; sleep 2.2; screencap -p $O/det_${ST}_0.png
  # to Details: right from Play, then A
  k KEYCODE_DPAD_RIGHT; sleep 0.5; k KEYCODE_DPAD_CENTER; sleep 1.4; screencap -p $O/det_${ST}_1.png
  for i in 1 2 3; do k KEYCODE_DPAD_DOWN; sleep 0.4; done; sleep 0.6; screencap -p $O/det_${ST}_2.png
  k KEYCODE_BUTTON_B; sleep 1.2; screencap -p $O/det_${ST}_3.png
  echo "${TAG}_det_$ST" > $F/qa_clip_req; sleep 1.1; cp $F/qa_clip/${TAG}_det_$ST.json $O/ 2>/dev/null
  k KEYCODE_BUTTON_B; sleep 1.2; screencap -p $O/det_${ST}_4.png
  echo "det $ST" >> $L
done
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null) real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L
