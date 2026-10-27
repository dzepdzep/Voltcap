# VoltCap QA — every home layout, one after the other, for this session only (voltcap_test_preview: nothing saved; a
# restart of Voltcap brings Toni's own layout back). Per layout: the same navigation recorded at 120 fps (half size),
# frame stats, the QaTrace log (slow frames, recompositions, focus).
#   sh layouts.sh RUN "CLASSIC TILES HERO …"   → out/RUN_<LAYOUT>.mp4 / _summary.txt / _qa.txt
run=$1; list=$2
NAV="R R R R R R W400 L L L L W300 D D W300 U U W300 R2 W900 R R R W300 L2 W900"
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "layouts $run ABORTED (not on home)"; exit 1; }
for L in $list; do
  am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview true --es voltcap_test_layout $L > /dev/null 2>&1; sleep 4
  am start -n com.shelf.launcher/.MainActivity --es voltcap_test_category ALL > /dev/null 2>&1; sleep 3
  sh vqa.sh seq "R L" 300 > /dev/null; sleep 1
  logcat -c; dumpsys gfxinfo com.shelf.launcher reset > /dev/null
  (screenrecord --size 960x540 --time-limit 11 out/${run}_$L.mp4 > /dev/null 2>&1 &)
  sleep 1; log -t VoltcapQA "MARK layout $L"
  sh vqa.sh seq "$NAV" 300 > /dev/null; sleep 2
  sh vqa.sh perf_dump ${run}_$L > /dev/null 2>&1
  logcat -d -s VoltcapQA > out/${run}_${L}_qa.txt
  sleep 1
  echo "$L done"
done
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200
echo "layouts $run done"
