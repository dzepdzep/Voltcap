# VoltCap QA (pass 5) — Settings › Categories › Category screen › Follow home layout: the category screen opened (B) from
# several home layouts with the switch on; one screenshot each (the screen's title names the look). Session only.
#   sh follow.sh RUN "STACKS TV LIBRARY MEMCARD DASH"   → out/RUN_<LAYOUT>.png
run=$1; list=$2
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "follow ABORTED"; exit 1; }
A="am start -n com.shelf.launcher/.MainActivity"
$A --es voltcap_test_snapshot save > /dev/null 2>&1; sleep 1
$A --es voltcap_test_pref pb_follow_home=true > /dev/null 2>&1; sleep 1
for L in $list; do
  $A --ez voltcap_test_preview true --es voltcap_test_layout $L > /dev/null 2>&1; sleep 4
  sh vqa.sh seq "B" 900 > /dev/null; sleep 1.8
  screencap -p out/${run}_$L.png
  sh vqa.sh seq "B" 700 > /dev/null; sleep 1
  echo "$L done"
done
$A --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null; am start -n com.shelf.launcher/.MainActivity > /dev/null 2>&1; echo "follow $run done"
