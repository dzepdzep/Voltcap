# VoltCap QA — one still per home layout at the device's native resolution (clipping, overlap, cut text), fast:
# preview only (nothing saved), the "All games" category, one step right and back, then the screenshot.
#   sh stills.sh RUN "CLASSIC TILES …"   → out/RUN_<LAYOUT>.png
run=$1; list=$2
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "stills $run ABORTED (not on home)"; exit 1; }
A="am start -n com.shelf.launcher/.MainActivity"
for L in $list; do
  $A --ez voltcap_test_preview true --es voltcap_test_layout $L > /dev/null 2>&1; sleep 3
  $A --es voltcap_test_category ALL > /dev/null 2>&1; sleep 2
  sh vqa.sh seq "R L" 300 > /dev/null; sleep 2.5
  screencap -p out/${run}_${L}.png
  echo "$L done"
done
$A --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200
echo "stills $run done"
