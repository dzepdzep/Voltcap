# Batch 523 (re-run): KONKR (GT78) memory, scrolling and controller regression on the isolated QA app ONLY, WITH the stand-in
# library on every start (QA_FIX=true: the first run's QA app restarted with an empty library - "No games yet" - so it was void).
# The real Voltcap 0.6.67 is never touched: no HOME press, no install, no sync.
export QA_FIX=true
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; D=/data/local/tmp; S=/sdcard/pc/konkr523c.txt; O=$F/k523c; mkdir -p $O
A() { am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true "$@" >/dev/null 2>&1; }
echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
sh $D/qasetup521.sh >> $S 2>&1
A --ez voltcap_test_preview true; sleep 2; A --es voltcap_test_layout STACKS --es voltcap_test_game "Mario Kart 64"; sleep 6; screencap -p $O/check_stacks.png
A --es voltcap_test_layout SHELF --es voltcap_test_game "Mario Kart 64"; sleep 6; screencap -p $O/check_shelf.png; A --ez voltcap_test_preview false; sleep 1
echo "library check shots taken $(date +%T)" >> $S
sh $D/mem520.sh m523c 3 > /dev/null 2>&1; echo "mem done $(date +%T)" >> $S
sh $D/perf514.sh k523cstacks STACKS > /dev/null 2>&1; screencap -p $O/after_perf_stacks.png
sh $D/perf514.sh k523cshelf SHELF > /dev/null 2>&1; screencap -p $O/after_perf_shelf.png; echo "perf done $(date +%T)" >> $S
sh $D/ctl516.sh c523c "GRID HERO_TILES CINEMA CASEFLOW BOX STORAGE FILESELECT SHELF TILES STACKS CHAN" > /dev/null 2>&1; echo "ctl done $(date +%T)" >> $S
sh $D/q520b.sh q523c > /dev/null 2>&1; echo "q520 done $(date +%T)" >> $S
am force-stop $P
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo DONE >> $S
