# VoltCap QA R24 (update 394) — category-switch visual test: a category change must never visually reload an unchanged
# layout. Per layout (session only, preview: nothing saved): repeated L2 / R2, rapid reversals (150 ms), a double step; the
# screen recorded at half size / full rate for tools/catswitch.py (frames.py: black / white / dip frames, transient flashes).
#   sh catswitch.sh RUN "CLASSIC TILES …"   → out/RUN_<LAYOUT>.mp4 / _qa.txt / .png + out/RUN_manifest.txt
# Pull out/ to qa_runs/<run>/out, then:  python3 tools/qa/tools/catswitch.py qa_runs/<run>/out RUN
run=$1; list=$2; PRE=$3   # PRE (update 396): keys pressed once per layout before the recording, e.g. "A" opens a category on Storage boxes / File select (they start on the category screen)
. ./guard.sh
NAV=${NAV:-"R2 W900 R2 W900 R2 W900 L2 W900 L2 W900 L2 W900 R2 W150 L2 W150 R2 W150 L2 W600 R2 W100 R2 W800 L2 W800"}
man_start $run; for L in $list; do man_expect $run $L; done
sh vqa.sh hooks on > /dev/null; sleep 2
# (no `ready` here: it presses R L and fails on layouts whose menu rail logs no game focus; each layout is checked below
# with screen_state, so a picker / non-home screen is a SKIP, never a pass)
# The owner's "default layout for categories" (cat_layout_default) would switch the previewed layout on the first category
# step (found in update 394: a whole run showed Artwork library): each tested layout is its own default for the session, the
# settings snapshot is put back at the end.
# 682j-c (owner 18 Nov 14:54): every key through winguard.sh - L2 / R2 only while the QA app's confirmed home window has the
# focus and shows HOME; the picker is passed once at setup (one B, confirmed), any other window = ABORT, nothing retried.
# 682l (anchor review, owner 18 Nov): this script pointed at the owner's real Voltcap (com.shelf.launcher/.MainActivity) - a
# QA script never may (CLAUDE.md Level 1). It drives the isolated QA app only now, every key gated (awake, unlocked, QA app in
# front - else the run stops with nothing pressed; a quick focus check before each key), and ANCH=off|on (QA-only voltcap_test_anchor_off) records the same walk with
# the Play / chip anchor rule off and on. With DUMPS=1 each settled step leaves a QaClip dump (out/RUN_LAYOUT_sN.json) for
# anchorcheck.py; the QA trace's "category" lines give the order of the walk (catorder682.py).
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files
A="am start -n $P/com.shelf.launcher.MainActivity"
gate() { dumpsys power | grep -q "mWakefulness=Awake" || { echo "STOP asleep"; man_end $run; exit 3; }
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && { echo "STOP locked"; man_end $run; exit 3; }
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || { echo "STOP QA app not in front"; man_end $run; exit 3; }; }
# (a key's own check, ~50 ms: the window with the focus is the QA app's - else stop, nothing pressed)
fgk() { dumpsys window | grep mCurrentFocus | tail -1 | grep -q "$P/" || { echo "STOP the QA app lost the focus"; man_end $run; exit 3; }; }
kc() { case $1 in R2) echo BUTTON_R2;; L2) echo BUTTON_L2;; R1) echo BUTTON_R1;; L1) echo BUTTON_L1;; B) echo BUTTON_B;; A) echo BUTTON_A;;
  L) echo DPAD_LEFT;; R) echo DPAD_RIGHT;; U) echo DPAD_UP;; D) echo DPAD_DOWN;; esac; }
dr() { [ -s "$1" ] && [ "$(tail -c 1 "$1" 2>/dev/null)" = "}" ]; }
dump() { rm -f $F/qa_clip/$1.json; echo "$1" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; i=0
  while ! dr $F/qa_clip/$1.json && [ $i -lt 30 ]; do sleep 0.2; i=$((i + 1)); done; dr $F/qa_clip/$1.json && cp $F/qa_clip/$1.json out/$1.json; }
nav() { n=0; for k in $1; do case $k in
    W*) ms=${k#W}; if [ "$DUMPS" = 1 ] && [ $ms -ge 800 ]; then sleep 0.6; n=$((n + 1)); [ "$SHOTS" = 1 ] && screencap -p out/${run}_${L}_s$n.png; dump ${run}_${L}_s$n; else sleep $(awk "BEGIN{print $ms/1000}"); fi;;
    *) wg_key_home $k;;
  esac; done; }
WG_LOG=out/${run}_guard.txt; TAG=$run
putback() { man_end $run; $A --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1; $A --ez voltcap_test_preview false > /dev/null 2>&1
  [ -n "$ANCH" ] && $A --ez voltcap_test_anchor_off false > /dev/null 2>&1; sh vqa.sh hooks off > /dev/null; am force-stop $P; }
WG_ONABORT=putback
. ./winguard.sh
# (the QA app's own stand-in library, launches blocked - hooks on restarted it)
$A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true > /dev/null 2>&1; sleep 6
[ -n "$ANCH" ] && { [ "$ANCH" = off ] && $A --ez voltcap_test_anchor_off true > /dev/null 2>&1 || $A --ez voltcap_test_anchor_off false > /dev/null 2>&1; sleep 1; }
$A --es voltcap_test_snapshot save > /dev/null 2>&1; sleep 1
for L in $list; do
  $A --es voltcap_test_pref "cat_layout_default=$L" > /dev/null 2>&1; sleep 0.5
  $A --ez voltcap_test_preview true --es voltcap_test_layout $L > /dev/null 2>&1; sleep 4
  $A --es voltcap_test_category ALL > /dev/null 2>&1; sleep 3
  wg_pass_picker
  man_screen $run $L
  [ "$(screen_state)" = HOME ] || { man_skip $run $L "not on the home screen ($(screen_state))"; echo "$L SKIPPED: not home"; continue; }
  wg_home
  [ -n "$PRE" ] && { nav "$PRE"; sleep 3; wg_home; }
  input keyevent KEYCODE_SHIFT_LEFT   # (keep-awake, straight after wg_home confirmed the QA home)
  screencap -p out/${run}_${L}.png
  logcat -c
  (nohup screenrecord --size 960x540 --time-limit ${REC:-17} out/${run}_$L.mp4 > /dev/null 2>&1 &)
  sleep 1; log -p e -t VoltcapQA "MARK layout $L"
  nav "$NAV"; sleep 2
  # (682l: the recording ends with the walk - a longer REC never runs on into the next layout's switch)
  for rp in $(pidof screenrecord); do kill -INT $rp; done; sleep 1.5
  logcat -d -s VoltcapQA > out/${run}_${L}_qa.txt
  sleep 1
  man_done $run $L
  echo "$L done"
done
man_end $run
$A --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1
$A --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
[ -n "$ANCH" ] && $A --ez voltcap_test_anchor_off false > /dev/null 2>&1
sh vqa.sh hooks off > /dev/null
echo "catswitch $run finished"
