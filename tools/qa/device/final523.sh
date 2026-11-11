# Batch 523 final check (build 523w), isolated QA app ONLY: Cover details (art + details, 1 / 2 / 3 / many, Endless on / off,
# recordings), the row-style windows over the home after the header / category fade fix (approved popups, screenshots), and the
# popup gate for the layouts / styles 523v-w touched (6 layouts x 6 styles). Each part snapshots / restores the QA settings.
#   sh final523.sh PREFIX      (PREFIX: k = KONKR, f = Flip 2)
D=/data/local/tmp; X=$1; S=/sdcard/pc/final523$X.txt; echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
sh $D/cd523u.sh ${X}cd523w > /dev/null 2>&1; echo "cover done $(date +%T)" >> $S
ART=1 VS="approved" CASES="CLASSIC:ROW_REVEAL CLASSIC:ROW_OPENS CLASSIC:SHARED TILES:ROW_REVEAL TILES:SHARED COVERDETAILS:OWN CLASSIC:OWN STORAGE:OWN" sh $D/lab523.sh ${X}lab523w "Super Mario World" > /dev/null 2>&1; echo "lab done $(date +%T)" >> $S
am start -n com.shelf.launcher.qa/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true >/dev/null 2>&1; sleep 5
sh $D/pop522.sh ${X}p523w "CLASSIC TILES GRID HERO_TILES COVERDETAILS CASEFLOW" "OWN BANNER ROW_OPENS ROW_REVEAL SHARED CLASSIC" "Super Mario World" > /dev/null 2>&1; echo "gate done $(date +%T)" >> $S
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo CHAIN_DONE >> $S
