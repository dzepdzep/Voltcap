# Batch 523 final gate on the latest build (isolated QA app only): badge gallery (16 pages incl. PC / stores), then the COMPLETE
# popup regression - all 53 layouts x all 6 popup styles at x1.0, then the same 318 at font x1.3 / density x1.15 (QA-only
# voltcap_test_scale hook, never the system settings). Each pop522 run snapshots and restores the QA app's settings.
D=/data/local/tmp; S=/sdcard/pc/chain523.txt; echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
ALL="CLASSIC TILES HERO GRID TV DASH LIVE SIDE CROSS SPOT BAR SHELF HUB RESUME CHAN CIRCUIT LIBRARY STACKS TIMELINE ARCADE CONSOLES DISCS HERO_TILES TWIN_DESK TWIN_ICONS TWIN_SHOWCASE CRATE CARDS CUSTOM POCKET_CLASSIC POCKET_RIBBON GREENCORE POCKET CUBE MEMCARD CASEFLOW BOX STORAGE FILESELECT ORBIT AIRY NIGHT BLADES BUBBLES CAMERA PANELS MAGAZINE SHOP TERMINAL CINEMA ARCTIC COVERDETAILS ARTLIB"
STY="OWN BANNER ROW_OPENS ROW_REVEAL SHARED CLASSIC"
sh $D/badge521.sh b523 16 > /dev/null 2>&1; echo "badges done $(date +%T)" >> $S
sh $D/pop522.sh p523all "$ALL" "$STY" "Super Mario World" > /dev/null 2>&1; echo "x1.0 done $(date +%T)" >> $S
sh $D/pop522.sh p523big "$ALL" "$STY" "Super Mario World" "font=1.3,density=1.15" > /dev/null 2>&1; echo "x1.3 done $(date +%T)" >> $S
echo "real=$(pidof com.shelf.launcher)" >> $S; echo CHAIN_DONE >> $S
