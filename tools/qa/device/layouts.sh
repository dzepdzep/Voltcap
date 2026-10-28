# VoltCap QA — every home layout, one after the other, for this session only (voltcap_test_preview: nothing saved; a
# restart of Voltcap brings Toni's own layout back). Per layout: the same navigation recorded at 120 fps (half size),
# frame stats, the QaTrace log (slow frames, recompositions, focus).
#   sh layouts.sh RUN "CLASSIC TILES HERO …"   → out/RUN_<LAYOUT>.mp4 / _summary.txt / _qa.txt / .png + out/RUN_manifest.txt
# Validate on the host: python3 tools/qa/tools/runcheck.py qa_runs/<run>/out RUN   (a layout that was skipped on the
# profile picker, recorded nothing or was cut short is SKIP / INFRA there, never PASS). Needs guard.sh + picker.sh next to vqa.sh.
run=$1; list=$2
. ./guard.sh
NAV="R R R R R R W400 L L L L W300 D D W300 U U W300 R2 W900 R R R W300 L2 W900"
man_start $run; for L in $list; do man_expect $run $L; done
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "layouts $run ABORTED (not on home)"; exit 1; }
for L in $list; do
  am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview true --es voltcap_test_layout $L > /dev/null 2>&1; sleep 4
  am start -n com.shelf.launcher/.MainActivity --es voltcap_test_category ALL > /dev/null 2>&1; sleep 3
  # "Who's playing?" can open over a layout on a (re)start: B keeps the current profile (nothing is changed), then look again (up to 3 tries; still PICKER = skipped, never measured)
  for try in 1 2 3; do [ "$(screen_state)" = PICKER ] || break; sh vqa.sh seq "B" 300 > /dev/null; sleep 3; done
  man_screen $run $L
  [ "$(screen_state)" = HOME ] || { man_skip $run $L "not on the home screen ($(screen_state))"; echo "$L SKIPPED: not home"; continue; }
  sh vqa.sh seq "R L" 300 > /dev/null; sleep 1
  screencap -p out/${run}_${L}.png   # the still at native resolution (clipping / overlap / cut text)
  logcat -c; dumpsys gfxinfo com.shelf.launcher reset > /dev/null
  (nohup screenrecord --size 960x540 --time-limit 11 out/${run}_$L.mp4 > /dev/null 2>&1 &)
  sleep 1; log -p e -t VoltcapQA "MARK layout $L"
  sh vqa.sh seq "$NAV" 300 > /dev/null; sleep 2
  sh vqa.sh perf_dump ${run}_$L > /dev/null 2>&1
  logcat -d -s VoltcapQA > out/${run}_${L}_qa.txt
  sleep 1
  man_done $run $L
  echo "$L done"
done
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200
man_end $run
echo "layouts $run done (validate with tools/runcheck.py: 'done' only means the script ran)"
