# Batch 523 (build 523f): KONKR (GT78) on the isolated QA app ONLY, stand-in library on every start (QA_FIX): Case flow /
# Game shelf / Case stacks scrolling (CoverFlow continuous movement on the KONKR), Endless + Case flow few-games shots and
# recordings, pull-out / flight / Details-in-window recordings, controller contract, memory soak. Real Voltcap never touched.
export QA_FIX=true
P=com.shelf.launcher.qa; D=/data/local/tmp; S=/sdcard/pc/konkr523f.txt; mkdir -p /sdcard/pc
echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
sh $D/qasetup521.sh >> $S 2>&1
sh $D/perf514.sh k523fflow CASEFLOW > /dev/null 2>&1; echo "perf caseflow done $(date +%T)" >> $S
sh $D/perf514.sh k523fshelf SHELF > /dev/null 2>&1; echo "perf shelf done $(date +%T)" >> $S
sh $D/perf514.sh k523fstacks STACKS > /dev/null 2>&1; echo "perf stacks done $(date +%T)" >> $S
sh $D/endless523b.sh ke523f > /dev/null 2>&1; echo "endless done $(date +%T)" >> $S
sh $D/motion523.sh km523f > /dev/null 2>&1; echo "motion done $(date +%T)" >> $S
sh $D/ctl516.sh kc523f "GRID HERO_TILES CINEMA CASEFLOW BOX STORAGE FILESELECT SHELF TILES STACKS CHAN COVERDETAILS" > /dev/null 2>&1; echo "ctl done $(date +%T)" >> $S
sh $D/mem520.sh km523f 3 > /dev/null 2>&1; echo "mem done $(date +%T)" >> $S
am force-stop $P
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo DONE >> $S
