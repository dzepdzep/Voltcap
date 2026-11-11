# Batch 523: KONKR (GT78) regression of the 520-522 changes on the isolated QA app ONLY: controller pass on the changed layouts
# (ctl516: D-pad, R1 / L1 panels, R2 / L2 categories, Select menu, Start options, B back - never A in R1) + the Game shelf
# pulled-out case with wide / portrait art (q520b). Settings snapshotted / restored by each script; no HOME press.
D=/data/local/tmp; S=/sdcard/pc/konkr523b.txt; echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
sh $D/ctl516.sh c523k "GRID HERO_TILES CINEMA CASEFLOW BOX STORAGE FILESELECT SHELF TILES STACKS CHAN" > /dev/null 2>&1; echo "ctl done $(date +%T)" >> $S
sh $D/q520b.sh q523k > /dev/null 2>&1; echo "q520 done $(date +%T)" >> $S
am force-stop com.shelf.launcher.qa
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo DONE >> $S
