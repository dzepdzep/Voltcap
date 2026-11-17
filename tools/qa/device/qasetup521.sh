#!/system/bin/sh
# Batch 521: a fresh isolated QA app's stand-in library (empty files in ITS OWN external files/roms; nothing of the owner's),
# then the QA-only rescan. sh qasetup521.sh
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; R=$F/roms
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
# (678l: the list lives in standins.sh, shared with pop529.sh; it also makes the files as the app when the shell is refused.)
sh $(dirname "$0")/standins.sh
A --ez voltcap_test_nolaunch true --ez voltcap_test_fixture_roms true; sleep 12
echo "roms=$(find $R -type f | wc -l) screen=$(cat $F/voltcap_qa_screen 2>/dev/null | head -1) real=$(pidof com.shelf.launcher)"
