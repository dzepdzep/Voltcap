#!/system/bin/sh
# Batch 523: a deleted game leaves the list on the next scan, also when its playlist / cue sheet stays behind. Isolated QA
# app ONLY, in its OWN stand-in library folder (Android/data/<qa>/files/roms - never the owner's ROMs). Test files are made
# here and removed again at the end. Grid layout, PS1 category, preview; settings snapshotted / restored.  sh deleted523.sh TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; R=$F/roms/psx; TAG=$1; O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true "$@" >/dev/null 2>&1; }
# (Batch 523: keys only while a QA app window has focus - a crash dialog's buttons lead into Settings' App info.)
k() { for w in 1 2 3 4 5 6; do f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *Application*) ;; *com.shelf.launcher.qa*) break;; esac; sleep 0.5; done
  case "$f" in *Application*) echo "ABORT focus=$f" >> $L; am force-stop com.shelf.launcher.qa; exit 2;; *com.shelf.launcher.qa*) input keyevent $1;; *) echo "ABORT focus=$f" >> $L; am force-stop com.shelf.launcher.qa; exit 2;; esac; }
pk() { grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { k KEYCODE_BUTTON_B; sleep 1.5; }; }
shot() { A --ez voltcap_test_preview true; A --es voltcap_test_layout GRID --es voltcap_test_category PS1; sleep 4; pk; screencap -p $O/$1.png
  echo "${TAG}_$1" > $F/qa_clip_req; sleep 1.2; cp $F/qa_clip/${TAG}_$1.json $O/ 2>/dev/null; }
input keyevent KEYCODE_WAKEUP; A --ez voltcap_test_nolaunch true; sleep 4; pk
A --es voltcap_test_snapshot save; sleep 2
# the test games: a two-disc set with its playlist, and a cue / bin game
echo 1 > "$R/QA Delete Set (Disc 1).chd"; echo 2 > "$R/QA Delete Set (Disc 2).chd"
printf 'QA Delete Set (Disc 1).chd\nQA Delete Set (Disc 2).chd\n' > "$R/QA Delete Set.m3u"
echo b > "$R/QA Delete Cue.bin"; printf 'FILE "QA Delete Cue.bin" BINARY\n  TRACK 01 MODE2/2352\n' > "$R/QA Delete Cue.cue"
am force-stop $P; sleep 1; A; sleep 6; shot 1_added
# the owner deletes the games' files - the playlist and the sheet stay
rm "$R/QA Delete Set (Disc 1).chd" "$R/QA Delete Set (Disc 2).chd" "$R/QA Delete Cue.bin"
am force-stop $P; sleep 1; A; sleep 6; shot 2_deleted
ls "$R" >> $L
# clean up the leftovers made by this test
rm -f "$R/QA Delete Set.m3u" "$R/QA Delete Cue.cue"
am force-stop $P; sleep 1; A; sleep 6; shot 3_cleaned
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null) real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L
