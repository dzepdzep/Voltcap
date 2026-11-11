# Batch 523u on the KONKR GT78 (BW0306N230000845), isolated QA app ONLY: popups before / after the approved direction, Cover
# details with art + details, then the popup gate x1.0 and x1.3 (all 318). Each part snapshots / restores the QA settings.
D=/data/local/tmp; S=/sdcard/pc/chain523u.txt; echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
ART=1 VS="legacy approved" CASES="CLASSIC:OWN TILES:OWN STORAGE:OWN HERO:OWN SPOT:OWN GRID:OWN SHELF:OWN COVERDETAILS:OWN CLASSIC:ROW_REVEAL CLASSIC:ROW_OPENS CLASSIC:SHARED CLASSIC:BANNER" sh $D/lab523.sh klab523u "Super Mario World" > /dev/null 2>&1; echo "lab done $(date +%T)" >> $S
sh $D/cd523u.sh kcd523u > /dev/null 2>&1; echo "cover done $(date +%T)" >> $S
am start -n com.shelf.launcher.qa/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true >/dev/null 2>&1; sleep 5
ALL="CLASSIC TILES HERO GRID TV DASH LIVE SIDE CROSS SPOT BAR SHELF HUB RESUME CHAN CIRCUIT LIBRARY STACKS TIMELINE ARCADE CONSOLES DISCS HERO_TILES TWIN_DESK TWIN_ICONS TWIN_SHOWCASE CRATE CARDS CUSTOM POCKET_CLASSIC POCKET_RIBBON GREENCORE POCKET CUBE MEMCARD CASEFLOW BOX STORAGE FILESELECT ORBIT AIRY NIGHT BLADES BUBBLES CAMERA PANELS MAGAZINE SHOP TERMINAL CINEMA ARCTIC COVERDETAILS ARTLIB"
STY="OWN BANNER ROW_OPENS ROW_REVEAL SHARED CLASSIC"
sh $D/pop522.sh kp523u "$ALL" "$STY" "Super Mario World" > /dev/null 2>&1; echo "x1.0 done $(date +%T)" >> $S
am start -n com.shelf.launcher.qa/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true >/dev/null 2>&1; sleep 5
sh $D/pop522.sh kpb523u "$ALL" "$STY" "Super Mario World" "font=1.3,density=1.15" > /dev/null 2>&1; echo "x1.3 done $(date +%T)" >> $S
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo CHAIN_DONE >> $S
