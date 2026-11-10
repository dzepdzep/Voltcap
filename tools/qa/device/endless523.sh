#!/system/bin/sh
# Batch 523: Endless with few games, isolated QA app ONLY. Per layout x category (1, 2, 3, many games): Endless on, the start,
# then 6 fast steps (80 ms apart) and 3 fast steps back, a shot after each burst; QaClip dump for the focus. Settings
# snapshotted / restored.  sh endless523.sh TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
input keyevent KEYCODE_WAKEUP; A --ez voltcap_test_nolaunch true; sleep 3
grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; }
A --es voltcap_test_snapshot save; sleep 2; A --ez voltcap_test_preview true; sleep 1
A --es voltcap_test_pref "loop_row3=true"; sleep 1
for LY in NIGHT AIRY CLASSIC ORBIT; do
  case $LY in NIGHT) FW=DPAD_DOWN; BK=DPAD_UP;; *) FW=DPAD_RIGHT; BK=DPAD_LEFT;; esac
  for CAT in "Game Boy" "Nintendo 64" "Nintendo GameCube" ALL; do
    C=$(echo "$CAT" | tr -d " ")
    A --ez voltcap_test_preview true; A --es voltcap_test_layout $LY --es voltcap_test_category "$CAT"; sleep 3
    grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; }
    screencap -p $O/${LY}_${C}_0.png
    for i in 1 2 3 4 5 6; do input keyevent KEYCODE_$FW; sleep 0.08; done; sleep 1.2; screencap -p $O/${LY}_${C}_fw.png
    for i in 1 2 3; do input keyevent KEYCODE_$BK; sleep 0.08; done; sleep 1.2; screencap -p $O/${LY}_${C}_bk.png
    echo "${TAG}_${LY}_$C" > $F/qa_clip_req; sleep 1.1; cp $F/qa_clip/${TAG}_${LY}_$C.json $O/ 2>/dev/null
    echo "$LY $C" >> $L
  done
done
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null) real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L
