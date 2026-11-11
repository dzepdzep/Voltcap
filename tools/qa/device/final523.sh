# Batch 523 final check (build 523w), isolated QA app ONLY: Cover details (art + details, 1 / 2 / 3 / many, Endless on / off,
# recordings), the row-style windows over the home after the header / category fade fix (approved popups, screenshots), and the
# popup gate for what 523v-w touch in code: COVERDETAILS (523v, StyleCoverDetails) and the layouts that draw the home's game header
# / category bar under a row-style window (523w: CLASSIC header + bar, TILES bar, GRID bar), plus HERO / SPOT (floating header
# parts - already faded by HomeEdit; controls) - 6 layouts x 6 styles at x1.0 AND x1.3 / density x1.15. Each part snapshots / restores the QA settings.
#   sh final523.sh PREFIX      (PREFIX: k = KONKR, f = Flip 2)
D=/data/local/tmp; X=$1; S=/sdcard/pc/final523$X.txt; echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
sh $D/cd523u.sh ${X}cd523w > /dev/null 2>&1; echo "cover done $(date +%T)" >> $S
ART=1 VS="approved" CASES="CLASSIC:ROW_REVEAL CLASSIC:ROW_OPENS CLASSIC:SHARED TILES:ROW_REVEAL TILES:ROW_OPENS TILES:SHARED GRID:ROW_REVEAL GRID:ROW_OPENS GRID:SHARED COVERDETAILS:OWN COVERDETAILS:ROW_REVEAL COVERDETAILS:SHARED" sh $D/lab523.sh ${X}lab523w "Super Mario World" > /dev/null 2>&1; echo "lab done $(date +%T)" >> $S
am start -n com.shelf.launcher.qa/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true >/dev/null 2>&1; sleep 5
L6="CLASSIC TILES GRID COVERDETAILS HERO SPOT"; ST="OWN BANNER ROW_OPENS ROW_REVEAL SHARED CLASSIC"
sh $D/pop522.sh ${X}p523w "$L6" "$ST" "Super Mario World" > /dev/null 2>&1; echo "gate x1.0 done $(date +%T)" >> $S
am start -n com.shelf.launcher.qa/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true >/dev/null 2>&1; sleep 5
sh $D/pop522.sh ${X}pb523w "$L6" "$ST" "Super Mario World" "font=1.3,density=1.15" > /dev/null 2>&1; echo "gate x1.3 done $(date +%T)" >> $S
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo CHAIN_DONE >> $S
