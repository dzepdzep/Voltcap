# Batch 523y (final build) on the KONKR GT78 or the RP Flip 2 - sh chain523y.sh k|f - isolated QA app ONLY: Cover details (art + details, recordings), the row-style
# windows over Classic / Tiles / Grid / Cover details after the header + category fade, then the FULL popup gate (318) at x1.0 and
# x1.3 / density x1.15 (the final build of Batch 523).
D=/data/local/tmp; X=${1:-k}; S=/sdcard/pc/chain523y$X.txt; echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
sh $D/cd523u.sh ${X}cd523y > /dev/null 2>&1; echo "cover done $(date +%T)" >> $S
ART=1 VS="approved" CASES="CLASSIC:ROW_REVEAL CLASSIC:ROW_OPENS CLASSIC:SHARED TILES:ROW_REVEAL TILES:ROW_OPENS TILES:SHARED TILES:OWN STORAGE:OWN CHAN:OWN ARCTIC:OWN POCKET_CLASSIC:OWN CLASSIC:BANNER GRID:ROW_REVEAL GRID:ROW_OPENS GRID:SHARED COVERDETAILS:OWN COVERDETAILS:ROW_REVEAL COVERDETAILS:SHARED" sh $D/lab523.sh ${X}lab523y "Super Mario World" > /dev/null 2>&1; echo "lab done $(date +%T)" >> $S
am start -n com.shelf.launcher.qa/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true >/dev/null 2>&1; sleep 5
ALL="CLASSIC TILES HERO GRID TV DASH LIVE SIDE CROSS SPOT BAR SHELF HUB RESUME CHAN CIRCUIT LIBRARY STACKS TIMELINE ARCADE CONSOLES DISCS HERO_TILES TWIN_DESK TWIN_ICONS TWIN_SHOWCASE CRATE CARDS CUSTOM POCKET_CLASSIC POCKET_RIBBON GREENCORE POCKET CUBE MEMCARD CASEFLOW BOX STORAGE FILESELECT ORBIT AIRY NIGHT BLADES BUBBLES CAMERA PANELS MAGAZINE SHOP TERMINAL CINEMA ARCTIC COVERDETAILS ARTLIB"
STY="OWN BANNER ROW_OPENS ROW_REVEAL SHARED CLASSIC"
sh $D/pop522.sh ${X}p523y "$ALL" "$STY" "Super Mario World" > /dev/null 2>&1; echo "x1.0 done $(date +%T)" >> $S
am start -n com.shelf.launcher.qa/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true >/dev/null 2>&1; sleep 5
sh $D/pop522.sh ${X}pb523y "$ALL" "$STY" "Super Mario World" "font=1.3,density=1.15" > /dev/null 2>&1; echo "x1.3 done $(date +%T)" >> $S
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo CHAIN_DONE >> $S
