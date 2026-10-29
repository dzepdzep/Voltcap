# VoltCap QA (update 389) — the selected card's rectangle per frame (QaGeom, ids fc*) while pressing ▶ / ◀ slowly, in a burst and
# reversed, one log per layout. Session only (voltcap_test_preview: nothing saved). Host: python3 tools/qa/tools/rowaudit.py
#   sh rowaudit.sh RUN "CLASSIC TILES HERO ..."   → out/RUN_<LAYOUT>_geom.txt
run=$1; list=$2
NAV="W600 R W1000 R W1000 R W1000 R R R R W1100 L W1000 L W1000 L L L W1100 R L R W900"
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "rowaudit ABORTED"; exit 1; }
for L in $list; do
  # (the geom intent first: an intent without the layout extras that comes after it left the layout on the previous one)
  am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_geom true > /dev/null 2>&1; sleep 1
  am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview true --es voltcap_test_layout $L > /dev/null 2>&1; sleep 4
  logcat -c; log -p e -t VoltcapQA "MARK layout $L"
  sh vqa.sh seq "$NAV" 100 > /dev/null; sleep 1
  logcat -d -s VoltcapQA > out/${run}_${L}_geom.txt
  screencap -p out/${run}_${L}.png
  echo "$L done"
done
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_geom false > /dev/null 2>&1
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null
echo "rowaudit $run done"
