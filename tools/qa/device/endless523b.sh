#!/system/bin/sh
# Batch 523 (v2): Endless with few games + Case flow continuous movement, isolated QA app ONLY, on the QA fixture library
# (GB 1 game, N64 2, GC 3, PS1 3, ALL many). Per layout x category x Endless fill (on = the owner's repeats, off = each game
# once): the start, 6 fast steps (80 ms) and 3 back, a shot after each burst; QaClip dump; Case flow also a 5 s screen
# recording of slow steps (frames checked on the PC). Settings snapshotted / restored.  sh endless523b.sh TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true "$@" >/dev/null 2>&1; }
# (Batch 523: keys only while a QA app window has focus - a crash dialog's buttons lead into Settings' App info.)
k() { for w in 1 2 3 4 5 6; do f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *Application*) ;; *com.shelf.launcher.qa*) break;; esac; sleep 0.5; done
  case "$f" in *Application*) echo "ABORT focus=$f" >> $L; am force-stop com.shelf.launcher.qa; exit 2;; *com.shelf.launcher.qa*) input keyevent $1;; *) echo "ABORT focus=$f" >> $L; am force-stop com.shelf.launcher.qa; exit 2;; esac; }
pk() { grep -q '^CRASH' $F/voltcap_qa_screen 2>/dev/null && { k KEYCODE_BUTTON_B; sleep 2; }; grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { k KEYCODE_BUTTON_B; sleep 1.5; }; }
input keyevent KEYCODE_WAKEUP; A --ez voltcap_test_nolaunch true; sleep 4; pk
A --es voltcap_test_snapshot save; sleep 2; A --ez voltcap_test_preview true; sleep 2
A --es voltcap_test_pref "loop_row3=true"; sleep 1
for FILL in false true; do
 A --es voltcap_test_pref "endless_fill=$FILL"; sleep 1
 for LY in CASEFLOW NIGHT AIRY CLASSIC ORBIT; do
  case $LY in NIGHT) FW=DPAD_DOWN; BK=DPAD_UP;; *) FW=DPAD_RIGHT; BK=DPAD_LEFT;; esac
  for CAT in GB N64 GC PS1 ALL; do
    N=${LY}_${CAT}_f$FILL
    A --ez voltcap_test_preview true; A --es voltcap_test_layout $LY --es voltcap_test_category "$CAT"; sleep 3; pk
    screencap -p $O/${N}_0.png
    for i in 1 2 3 4 5 6; do k KEYCODE_$FW; sleep 0.08; done; sleep 1.2; screencap -p $O/${N}_fw.png
    for i in 1 2 3; do k KEYCODE_$BK; sleep 0.08; done; sleep 1.2; screencap -p $O/${N}_bk.png
    echo "${TAG}_$N" > $F/qa_clip_req; sleep 1.1; cp $F/qa_clip/${TAG}_$N.json $O/ 2>/dev/null
    if [ $LY = CASEFLOW ] && [ $CAT != ALL ]; then
      screenrecord --time-limit 5 --bit-rate 8000000 $O/${N}.mp4 &
      R=$!; sleep 0.6
      for i in 1 2 3 4; do k KEYCODE_$FW; sleep 0.55; done; for i in 1 2; do k KEYCODE_$BK; sleep 0.55; done
      wait $R
    fi
    echo "$N" >> $L
  done
 done
done
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null) real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L
