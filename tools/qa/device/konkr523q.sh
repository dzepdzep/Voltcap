# Batch 523 on the KONKR GT78 (BW0306N230000845), isolated QA app ONLY (build 523o): controller contract on 16 layouts, then the
# popup gate x1.0 (53 layouts x 6 styles; the stand-in library loaded first - the GT78 has no QA library of its own). Every part snapshots and
# restores the QA app's settings; no key reaches anything but the QA app (focus guards in each script).
D=/data/local/tmp; S=/sdcard/pc/konkr523q.txt; mkdir -p /sdcard/pc; echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
QA_FIX=true sh $D/ctl516.sh kc523q "CLASSIC TILES SHELF STACKS COVERDETAILS CASEFLOW GRID HERO_TILES CINEMA BOX STORAGE FILESELECT CHAN TWIN_DESK CUBE ARCTIC" > /dev/null 2>&1; echo "ctl done $(date +%T)" >> $S
am start -n com.shelf.launcher.qa/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true >/dev/null 2>&1; sleep 5
ALL="CLASSIC TILES HERO GRID TV DASH LIVE SIDE CROSS SPOT BAR SHELF HUB RESUME CHAN CIRCUIT LIBRARY STACKS TIMELINE ARCADE CONSOLES DISCS HERO_TILES TWIN_DESK TWIN_ICONS TWIN_SHOWCASE CRATE CARDS CUSTOM POCKET_CLASSIC POCKET_RIBBON GREENCORE POCKET CUBE MEMCARD CASEFLOW BOX STORAGE FILESELECT ORBIT AIRY NIGHT BLADES BUBBLES CAMERA PANELS MAGAZINE SHOP TERMINAL CINEMA ARCTIC COVERDETAILS ARTLIB"
sh $D/pop522.sh kp523q "$ALL" "OWN BANNER ROW_OPENS ROW_REVEAL SHARED CLASSIC" "Super Mario World" > /dev/null 2>&1; echo "x1.0 done $(date +%T)" >> $S
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo CHAIN_DONE >> $S
