# Batch 523: KONKR (GT78) memory + scrolling on the isolated QA app ONLY (the real Voltcap 0.6.67 is its home app and is never
# touched: no HOME press, no install, no sync). Stand-in library -> memory soak (3 rounds) -> frame pacing on Case Stacks + Shelf.
D=/data/local/tmp; S=/sdcard/pc/konkr523.txt; mkdir -p /sdcard/pc; echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
sh $D/qasetup521.sh >> $S 2>&1
sh $D/mem520.sh m523k 3 > /dev/null 2>&1; echo "mem done $(date +%T)" >> $S
sh $D/perf514.sh k523stacks STACKS > /dev/null 2>&1
sh $D/perf514.sh k523shelf SHELF > /dev/null 2>&1; echo "perf done $(date +%T)" >> $S
am force-stop com.shelf.launcher.qa
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo DONE >> $S
