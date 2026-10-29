# VoltCap QA (update 407) — R23 one-press sweep over home layouts (session only: voltcap_test_preview, nothing saved).
#   cd /data/local/tmp/vqa; sh onepress_all.sh RUN "BOX COVERDETAILS ..." [SEQ]   → out/RUN_<LAYOUT>_op.log + .png
# Host: py -3 tools/qa/tools/focuscheck.py out/RUN_<LAYOUT>_op.log --size 53 --edge-ok   (category ALL = 53 games on the Thor)
# One press per key, 800 ms rest, forward then back from game 0; `vqa.sh onepress` itself refuses to run (INFRA) when the hooks
# are off or the "Who's playing?" picker is in front (D101: that, not a missing layout event, was the empty-log cause).
# READYKEYS="D U" for layouts whose games move on the vertical axis (HERO, TWIN_DESK ...).
run=$1; list=$2; seq=${3:-"R R R L L L"}
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "onepress_all ABORTED (INFRA)"; exit 1; }
for L in $list; do
  am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview true --es voltcap_test_layout $L --es voltcap_test_category ALL > /dev/null 2>&1; sleep 4
  logcat -c; log -p e -t VoltcapQA "MARK layout $L"
  sh vqa.sh onepress "$seq" 800 > out/${run}_${L}_op.txt 2>&1; rc=$?
  sleep 1; logcat -d -s VoltcapQA > out/${run}_${L}_op.log
  screencap -p out/${run}_${L}_op.png
  echo "$L rc=$rc"
done
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null
echo "onepress_all $run done"
