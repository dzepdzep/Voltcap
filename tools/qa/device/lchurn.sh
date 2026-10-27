# VoltCap QA — why a layout re-composes (QaInval + theme-read stacks) — session-only layouts, nothing saved.
#   sh lchurn.sh RUN "GRID CROSS TV"   → out/RUN_<LAYOUT>_qa.txt  (inval / themeread / churn lines)
run=$1; list=$2
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "lchurn ABORTED"; exit 1; }
for L in $list; do
  am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview true --es voltcap_test_layout $L > /dev/null 2>&1; sleep 4
  am start -n com.shelf.launcher/.MainActivity --es voltcap_test_category ALL > /dev/null 2>&1; sleep 3
  am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_churn true > /dev/null 2>&1; sleep 1
  logcat -c
  sh vqa.sh seq "R R R R R R L L L L" 300 > /dev/null; sleep 2
  logcat -d -s VoltcapQA > out/${run}_${L}_qa.txt
  am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_churn false > /dev/null 2>&1; sleep 1
  echo "$L done"
done
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200; echo "lchurn $run done"
