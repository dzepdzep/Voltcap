#!/system/bin/sh
# Batch 523r/t: Cover details recomposed (523t: with the fixture art + details) (big case upper left, angled 3D cases on the lower-right shelf). Isolated QA app ONLY,
# stand-in library, preview (nothing saved), settings snapshotted / restored, Performance Full for the run (QA hook).
# Per category (GB 1 game, N64 2, PS1 3, ALL many) x Endless on (fill on: the owner's default) / off: rest, 2 steps right,
# 1 step left (shots + QaClip dump); recordings of slow steps both ways for PS1 and ALL with Endless on.  sh cd523r.sh TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true "$@" >/dev/null 2>&1; }
k() { for w in 1 2 3 4 5 6 7 8 9 10; do f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *Application*) ;; *com.shelf.launcher.qa*) break;; *=null*) input keyevent KEYCODE_WAKEUP;; esac; sleep 0.5; done
  case "$f" in *Application*) echo "ABORT focus=$f" >> $L; am force-stop $P; exit 2;; *com.shelf.launcher.qa*) input keyevent $1;; *) echo "ABORT focus=$f" >> $L; am force-stop $P; exit 2;; esac; }
pk() { grep -q '^CRASH' $F/voltcap_qa_screen 2>/dev/null && { k KEYCODE_BUTTON_B; sleep 2; }; grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { k KEYCODE_BUTTON_B; sleep 1.5; }; }
echo "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher)" > $L
input keyevent KEYCODE_WAKEUP; A --ez voltcap_test_nolaunch true; sleep 4; pk
A --es voltcap_test_snapshot save; sleep 2
# (523t: covers, logos and details for the stand-in games - QaArtFixture, QA app only)
A --es voltcap_test_artfix "delay=0"; sleep 4
A --es voltcap_test_perf FULL; sleep 1
A --ez voltcap_test_preview true; sleep 1; A --es voltcap_test_pref "endless_fill_v2=true"; sleep 1
for LOOP in true false; do
 A --ez voltcap_test_preview true; sleep 0.5; A --es voltcap_test_loop $LOOP; sleep 1
 for CAT in GB N64 PS1 ALL; do
  N=${CAT}_loop$LOOP
  A --es voltcap_test_layout COVERDETAILS --es voltcap_test_category "$CAT"; sleep 3.5; pk
  screencap -p $O/${N}_0.png
  echo "${TAG}_${N}_0" > $F/qa_clip_req; sleep 1.1
  k KEYCODE_DPAD_RIGHT; sleep 1.4; screencap -p $O/${N}_r1.png
  k KEYCODE_DPAD_RIGHT; sleep 1.4; screencap -p $O/${N}_r2.png
  echo "${TAG}_${N}_r2" > $F/qa_clip_req; sleep 1.1
  k KEYCODE_DPAD_LEFT; sleep 1.4; screencap -p $O/${N}_l1.png
  if [ $LOOP = true ] && { [ $CAT = PS1 ] || [ $CAT = ALL ]; }; then
    screenrecord --time-limit 9 --bit-rate 10000000 $O/${N}.mp4 &
    R=$!; sleep 0.8
    for i in 1 2 3; do k KEYCODE_DPAD_RIGHT; sleep 1.0; done; for i in 1 2 3; do k KEYCODE_DPAD_LEFT; sleep 1.0; done
    wait $R
  fi
  cp $F/qa_clip/${TAG}_${N}_* $O/ 2>/dev/null
  echo "$N" >> $L
 done
done
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_perf AUTO; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null) real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $L; echo DONE >> $L
