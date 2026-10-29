# VoltCap QA R24 (update 394) — category-switch visual test: a category change must never visually reload an unchanged
# layout. Per layout (session only, preview: nothing saved): repeated L2 / R2, rapid reversals (150 ms), a double step; the
# screen recorded at half size / full rate for tools/catswitch.py (frames.py: black / white / dip frames, transient flashes).
#   sh catswitch.sh RUN "CLASSIC TILES …"   → out/RUN_<LAYOUT>.mp4 / _qa.txt / .png + out/RUN_manifest.txt
# Pull out/ to qa_runs/<run>/out, then:  python3 tools/qa/tools/catswitch.py qa_runs/<run>/out RUN
run=$1; list=$2; PRE=$3   # PRE (update 396): keys pressed once per layout before the recording, e.g. "A" opens a category on Storage boxes / File select (they start on the category screen)
. ./guard.sh
NAV="R2 W900 R2 W900 R2 W900 L2 W900 L2 W900 L2 W900 R2 W150 L2 W150 R2 W150 L2 W600 R2 W100 R2 W800 L2 W800"
man_start $run; for L in $list; do man_expect $run $L; done
sh vqa.sh hooks on > /dev/null
# (no `ready` here: it presses R L and fails on layouts whose menu rail logs no game focus; each layout is checked below
# with screen_state, so a picker / non-home screen is a SKIP, never a pass)
# The owner's "default layout for categories" (cat_layout_default) would switch the previewed layout on the first category
# step (found in update 394: a whole run showed Artwork library): each tested layout is its own default for the session, the
# settings snapshot is put back at the end.
A="am start -n com.shelf.launcher/.MainActivity"
$A --es voltcap_test_snapshot save > /dev/null 2>&1; sleep 1
for L in $list; do
  $A --es voltcap_test_pref "cat_layout_default=$L" > /dev/null 2>&1; sleep 0.5
  am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview true --es voltcap_test_layout $L > /dev/null 2>&1; sleep 4
  am start -n com.shelf.launcher/.MainActivity --es voltcap_test_category ALL > /dev/null 2>&1; sleep 3
  for try in 1 2 3; do [ "$(screen_state)" = PICKER ] || break; sh vqa.sh seq "B" 300 > /dev/null; sleep 3; done
  man_screen $run $L
  [ "$(screen_state)" = HOME ] || { man_skip $run $L "not on the home screen ($(screen_state))"; echo "$L SKIPPED: not home"; continue; }
  [ -n "$PRE" ] && { sh vqa.sh seq "$PRE" 300 > /dev/null; sleep 3; }
  screencap -p out/${run}_${L}.png
  logcat -c
  (nohup screenrecord --size 960x540 --time-limit 17 out/${run}_$L.mp4 > /dev/null 2>&1 &)
  sleep 1; log -p e -t VoltcapQA "MARK layout $L"
  sh vqa.sh seq "$NAV" 250 > /dev/null; sleep 2
  logcat -d -s VoltcapQA > out/${run}_${L}_qa.txt
  sleep 1
  man_done $run $L
  echo "$L done"
done
man_end $run
$A --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200
echo "catswitch $run finished"
