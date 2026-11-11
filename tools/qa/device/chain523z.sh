# Batch 523z (fixes from the graded 523y gates) on the KONKR GT78 or the RP Flip 2 - sh chain523z.sh k|f - isolated QA app ONLY.
# What 523z touches, every layout, at x1.0 and x1.3 / density x1.15: OWN (Back hint under every own window; Camera rows),
# ROW_OPENS / ROW_REVEAL (Details room). The other styles are unchanged since 523y and keep its graded results.
D=/data/local/tmp; X=${1:-k}; S=/sdcard/pc/chain523z$X.txt; echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
am start -n com.shelf.launcher.qa/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true >/dev/null 2>&1; sleep 5
ALL="CLASSIC TILES HERO GRID TV DASH LIVE SIDE CROSS SPOT BAR SHELF HUB RESUME CHAN CIRCUIT LIBRARY STACKS TIMELINE ARCADE CONSOLES DISCS HERO_TILES TWIN_DESK TWIN_ICONS TWIN_SHOWCASE CRATE CARDS CUSTOM POCKET_CLASSIC POCKET_RIBBON GREENCORE POCKET CUBE MEMCARD CASEFLOW BOX STORAGE FILESELECT ORBIT AIRY NIGHT BLADES BUBBLES CAMERA PANELS MAGAZINE SHOP TERMINAL CINEMA ARCTIC COVERDETAILS ARTLIB"
STY="OWN ROW_OPENS ROW_REVEAL"
sh $D/pop522.sh ${X}p523z "$ALL" "$STY" "Super Mario World" > /dev/null 2>&1; echo "x1.0 done $(date +%T)" >> $S
am start -n com.shelf.launcher.qa/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true >/dev/null 2>&1; sleep 5
sh $D/pop522.sh ${X}pb523z "$ALL" "$STY" "Super Mario World" "font=1.3,density=1.15" > /dev/null 2>&1; echo "x1.3 done $(date +%T)" >> $S
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo CHAIN_DONE >> $S
