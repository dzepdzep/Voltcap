# Batch 523 (build 523l) on the RP Classic, isolated QA app ONLY: Smart App Artwork on apps without art, the Save Vault looks,
# the popup gate at font x1.3 / density x1.15 (all 53 layouts x 6 styles) and, at x1.0, the three styles whose Details changed
# since 523f (Row opens / Row reveal / Shared). Every part snapshots and restores the QA app's settings.
D=/data/local/tmp; S=/sdcard/pc/chain523l.txt; echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
ALL="CLASSIC TILES HERO GRID TV DASH LIVE SIDE CROSS SPOT BAR SHELF HUB RESUME CHAN CIRCUIT LIBRARY STACKS TIMELINE ARCADE CONSOLES DISCS HERO_TILES TWIN_DESK TWIN_ICONS TWIN_SHOWCASE CRATE CARDS CUSTOM POCKET_CLASSIC POCKET_RIBBON GREENCORE POCKET CUBE MEMCARD CASEFLOW BOX STORAGE FILESELECT ORBIT AIRY NIGHT BLADES BUBBLES CAMERA PANELS MAGAZINE SHOP TERMINAL CINEMA ARCTIC COVERDETAILS ARTLIB"
STY="OWN BANNER ROW_OPENS ROW_REVEAL SHARED CLASSIC"
QA_FIX=true sh $D/appart523.sh a523l "CLASSIC TILES GRID HERO HERO_TILES CINEMA DASH LIVE CHAN RESUME SPOT TV" "JoiPlay FolderSync Obtainium" > /dev/null 2>&1; echo "appart done $(date +%T)" >> $S
QA_FIX=true sh $D/vault523.sh v523l "CLASSIC ICONS PS2 MEMORY_CARD SD_CARD FLOPPY CAPSULE CARD_3D" > /dev/null 2>&1; echo "vault done $(date +%T)" >> $S
sh $D/pop522.sh p523lb "$ALL" "$STY" "Super Mario World" "font=1.3,density=1.15" > /dev/null 2>&1; echo "x1.3 done $(date +%T)" >> $S
sh $D/pop522.sh p523lr "$ALL" "ROW_OPENS ROW_REVEAL SHARED" "Super Mario World" > /dev/null 2>&1; echo "x1.0 rows done $(date +%T)" >> $S
echo "real=$(pidof com.shelf.launcher)" >> $S; echo CHAIN_DONE >> $S
