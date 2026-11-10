#!/system/bin/sh
# Batch 523: the GT78 523k crash (ClassCastException in LauncherScreen's composition, right after `voltcap_test_perf FULL`
# with a layout switch) - tried again: performance mode flipped with layout switches, 12 times. Isolated QA app ONLY, no keys.
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; O=/sdcard/pc/flip523; mkdir -p $O; L=$O/log.txt; echo "start $(date +%T)" > $L
A() { am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true "$@" >/dev/null 2>&1; }
logcat -c -b crash
A --ez voltcap_test_nolaunch true; sleep 4; A --es voltcap_test_snapshot save; sleep 2
for i in 1 2 3 4 5 6; do
  for LY in SHELF COVERDETAILS CASEFLOW STACKS; do
    A --ez voltcap_test_preview true; A --es voltcap_test_perf FULL --es voltcap_test_layout $LY --es voltcap_test_category ALL; sleep 2.5
    A --es voltcap_test_perf LITE; sleep 1.5; A --es voltcap_test_perf AUTO; sleep 1
  done
  echo "round $i fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $L
done
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
logcat -d -b crash | grep -A6 "FATAL EXCEPTION" | head -30 >> $L
echo "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null) real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L
