# VoltCap QA (pass 5) — one home layout under atrace (gfx / view), RenderThread included, for render-bound layouts.
# Session only (preview), ends the preview.   sh ltrace2.sh RUN LAYOUT → out/RUN.atrace
run=$1; L=$2
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "ltrace2 ABORTED"; exit 1; }
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview true --es voltcap_test_layout $L > /dev/null 2>&1; sleep 5
sh vqa.sh seq "R L" 300 > /dev/null; sleep 1
sh vqa.sh atrace $run 9 > /dev/null; sleep 0.5
sh vqa.sh seq "R R R R R R W400 L L L L W300 R2 W900 R R W300 L2 W900" 300 > /dev/null
sleep 4
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null; echo "ltrace2 $run done $(pidof com.shelf.launcher)"
