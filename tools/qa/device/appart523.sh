#!/system/bin/sh
# Batch 523: Smart App Artwork - apps without cover art (on these handhelds: JoiPlay, FolderSync, Obtainium, SaveQuest; DroidDeck /
# PXPlay / Moonlight are not installed and nothing is installed for this test) in square, portrait, wide and hero cards, focus on.
# Isolated QA app ONLY, preview (nothing saved), settings snapshotted / restored.   sh appart523.sh TAG ["LAYOUTS"] ["APPS"]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
LAYS=${2:-"CLASSIC TILES GRID HERO HERO_TILES CINEMA DASH LIVE CHAN RESUME SPOT TV"}
APPS=${3:-"JoiPlay FolderSync Obtainium"}
A() { am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true "$@" >/dev/null 2>&1; }
# (Batch 523: keys only while a QA app window has focus - a crash dialog's buttons lead into Settings' App info.)
k() { for w in 1 2 3 4 5 6; do f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *Application*) ;; *com.shelf.launcher.qa*) break;; esac; sleep 0.5; done
  case "$f" in *Application*) echo "ABORT focus=$f" >> $L; am force-stop com.shelf.launcher.qa; exit 2;; *com.shelf.launcher.qa*) input keyevent $1;; *) echo "ABORT focus=$f" >> $L; am force-stop com.shelf.launcher.qa; exit 2;; esac; }
pk() { grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { k KEYCODE_BUTTON_B; sleep 1.5; }; }
input keyevent KEYCODE_WAKEUP; A --ez voltcap_test_nolaunch true; sleep 4; pk
A --es voltcap_test_snapshot save; sleep 2; A --ez voltcap_test_preview true; sleep 2
for LY in $LAYS; do
  for AP in $APPS; do
    A --ez voltcap_test_preview true; A --es voltcap_test_layout $LY --es voltcap_test_category ALL --es voltcap_test_game "$AP"; sleep 3.5; pk
    screencap -p $O/${LY}_${AP}.png; echo "$LY $AP" >> $L
  done
done
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null) real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L
