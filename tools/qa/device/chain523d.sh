# Batch 523 (build 523e) final device chain, isolated QA app ONLY: Endless few-games check, Smart Fill after-shots (WWE with
# 4 art shapes + Super Mario World real art), then the COMPLETE popup gate: 53 layouts x 6 styles at x1.0 and at font x1.3 /
# density x1.15 (QA-only scale hook). Every part snapshots and restores the QA app's settings.
D=/data/local/tmp; S=/sdcard/pc/chain523d.txt; echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
ALL="CLASSIC TILES HERO GRID TV DASH LIVE SIDE CROSS SPOT BAR SHELF HUB RESUME CHAN CIRCUIT LIBRARY STACKS TIMELINE ARCADE CONSOLES DISCS HERO_TILES TWIN_DESK TWIN_ICONS TWIN_SHOWCASE CRATE CARDS CUSTOM POCKET_CLASSIC POCKET_RIBBON GREENCORE POCKET CUBE MEMCARD CASEFLOW BOX STORAGE FILESELECT ORBIT AIRY NIGHT BLADES BUBBLES CAMERA PANELS MAGAZINE SHOP TERMINAL CINEMA ARCTIC COVERDETAILS ARTLIB"
STY="OWN BANNER ROW_OPENS ROW_REVEAL SHARED CLASSIC"
sh $D/endless523.sh e523 > /dev/null 2>&1; echo "endless done $(date +%T)" >> $S
sh $D/smart523.sh s523after "real portrait square wide" "WWE SmackDown" > /dev/null 2>&1; echo "smart done $(date +%T)" >> $S
sh $D/smart523.sh s523asmw "real" "Super Mario World" "TILES HERO CHAN SPOT ARTLIB CASEFLOW LIBRARY CINEMA DASH RESUME" > /dev/null 2>&1; echo "smart smw done $(date +%T)" >> $S
sh $D/pop522.sh p523e "$ALL" "$STY" "Super Mario World" > /dev/null 2>&1; echo "x1.0 done $(date +%T)" >> $S
sh $D/pop522.sh p523eb "$ALL" "$STY" "Super Mario World" "font=1.3,density=1.15" > /dev/null 2>&1; echo "x1.3 done $(date +%T)" >> $S
echo "real=$(pidof com.shelf.launcher)" >> $S; echo CHAIN_DONE >> $S
