# Game shelf: OLD vs NEW (3D case) pull-out, artwork readiness and spine flicker - QA app only. 
# Runs on the handheld from /data/local/tmp. Same gates as ci_qa.sh: a failed gate stops the script with no input sent.
# Nothing is saved: the layout is a preview (voltcap_test_preview, ended at the end of each run), the 3D switch is in memory (Shelf3D.enabled), games never start
# (voltcap_test_nolaunch). The real Voltcap is never launched, installed over, cleared or touched.
# usage:  sh shelf3d_qa.sh pre                         read-only: awake, unlocked, the real Voltcap NOT in front, QA app installed
#         (the real Voltcap's identity is checked by the PC tool install_build, before and after, not by this script)
#         sh shelf3d_qa.sh run MODE NAME [STEPS]       MODE old|new: open the Game shelf in the QA app, record STEPS
#                                                      D-pad moves (default 6) at 0.9 s, then back and forth fast (3 × ◀▶)
#         sh shelf3d_qa.sh pair NAME [STEPS]           run old then new with the same game and moves (the comparison)
#         COLD=1 sh shelf3d_qa.sh ...                  stop the QA app first (cold artwork); the real Voltcap is never stopped
#         SCANS=1 / ART_DELAY=800 sh shelf3d_qa.sh ... session-only scanned spines / fronts (scan, custom, none); fixture art held back (ms)
#         PICK_A=1 sh shelf3d_qa.sh ...                 answer the QA app's own profile picker with one A, only when its mark says PICKER
#         Every run checks the Game shelf is on screen (its mark + log) before recording or sending a move; else it stops (exit 5).
#         sh shelf3d_qa.sh trace NAME SECS             logcat of the QA trace (SpineTrace EMPTY / SWAP / REDECODE, hand-over)
P=com.shelf.launcher.qa; OUT=/sdcard/pc/shelf3d; mkdir -p $OUT
awake()  { dumpsys power | grep -q "mWakefulness=Awake"; }
locked() { dumpsys window | grep -qE "mDreamingLockscreen=true|isStatusBarKeyguard=true|mShowingLockscreen=true|isKeyguardShowing=true"; }
top()    { dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity"; }
gate_pre() {
  awake || { echo "STOP: screen asleep"; exit 3; }
  locked && { echo "STOP: locked"; exit 3; }
  pm path $P > /dev/null || { echo "STOP: QA app not installed"; exit 3; }
  # stricter than "the real Voltcap not in front" (the PC guard refuses scripts that name the real package): the QA app must be in front
  t=$(top); echo "$t" | grep -q "$P/" || { echo "STOP: QA app not in front ($t)"; exit 3; }
  return 0; }
gate_qa() {
  awake || { echo "STOP: screen asleep"; exit 3; }
  locked && { echo "STOP: locked"; exit 3; }
  t=$(top); echo "$t" | grep -q "$P/" || { echo "STOP: QA app not in front ($t)"; exit 3; }
  return 0; }
key() { gate_qa; input keyevent KEYCODE_$1 || { echo "STOP: input failed"; exit 4; }; }
MARK=/sdcard/Android/data/$P/files/voltcap_qa_screen
# Which screen the QA app says it shows (its own mark, written by its live process): SHELF, PICKER, HOME or UNKNOWN.
screen() { pid=$(pidof $P 2>/dev/null | tr ' ' '\n' | head -1); [ -n "$pid" ] || { echo UNKNOWN; return; }
  set -- $(cat $MARK 2>/dev/null); [ "$2" = "$pid" ] && echo "${1:-UNKNOWN}" || echo UNKNOWN; }
# The Game shelf is on screen: the QA app logged "screen SHELF" since the start intent, and its mark is not the picker.
shelf_ready() { gate_qa; [ "$(screen)" = PICKER ] && return 2
  logcat -d -s VoltcapQA 2>/dev/null | grep -q " screen SHELF$" && return 0; return 1; }
open_shelf() {   # $1 = true|false (the 3D case). COLD=1: the QA app (never the real one) is stopped first: cold artwork in memory
  gate_pre
  [ "$COLD" = 1 ] && { am force-stop $P; sleep 1; }
  X=""; [ "$SCANS" = 1 ] && X="--es voltcap_test_scanfix on"; [ -n "$ART_DELAY" ] && X="$X --es voltcap_test_artfix delay=$ART_DELAY"
  logcat -c
  am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_preview true --es voltcap_test_layout SHELF --ez voltcap_test_nolaunch true \
    --ez voltcap_test_shelf3d $1 $X > /dev/null || { echo "STOP: start failed"; exit 4; }
  # Nothing is recorded and no move is sent until the shelf is verified on screen (12 Nov: after a cold start the QA app's
  # "Who's playing?" came up and the D-pad moves went to it). The picker is answered only when PICK_A=1 and only while the QA
  # app's own mark says PICKER: one A (Play, the highlighted QA profile), then checked again; else the run stops.
  n=0; picked=0
  while [ $n -lt 16 ]; do
    sleep 0.5; shelf_ready; r=$?
    [ $r = 0 ] && { echo "SHELF on screen"; return 0; }
    if [ $r = 2 ]; then
      [ "$PICK_A" = 1 ] && [ $picked = 0 ] || { echo "STOP: the QA app shows its profile picker (PICK_A=1 answers it with A once)"; exit 5; }
      gate_qa; [ "$(screen)" = PICKER ] || continue
      input keyevent KEYCODE_BUTTON_A || { echo "STOP: input failed"; exit 4; }; picked=1; echo "picker: A once"
    fi
    n=$((n + 1))
  done
  echo "STOP: the Game shelf was not seen on screen (mark $(screen))"; exit 5; }
cmd=$1; shift
case $cmd in
  pre)  gate_pre; echo "PRE-OK $(getprop ro.product.model) $(wm size | tail -1) $(wm density | tail -1)"; top
        dumpsys package $P | grep -m2 -E "versionName|signatures" ;;
  run)  M=$1; N=$2; S=${3:-6}; [ "$M" = new ] && E=true || E=false
        open_shelf $E; dumpsys gfxinfo $P reset > /dev/null; logcat -c
        screenrecord --bit-rate 16000000 --time-limit $((S * 2 + 12)) $OUT/${N}_$M.mp4 & R=$!; sleep 0.8
        i=0; while [ $i -lt $S ]; do key DPAD_RIGHT; sleep 0.9; i=$((i + 1)); done
        for r in 1 2 3; do key DPAD_LEFT; sleep 0.12; key DPAD_RIGHT; sleep 0.12; done; sleep 1.5
        # back to the first game the same way (reverse turn / return to the shelf), so the next run starts on the same game
        i=0; while [ $i -lt $S ]; do key DPAD_LEFT; sleep 0.9; i=$((i + 1)); done; sleep 1.5
        kill -INT $R 2>/dev/null; sleep 0.8
        dumpsys gfxinfo $P | grep -E "Total frames|Janky|50th|90th|95th|99th" | tee $OUT/${N}_$M.gfx.txt
        logcat -d -s VoltcapQA | grep -E "spine |shelf3d|LAUNCH" > $OUT/${N}_$M.trace.txt; wc -l $OUT/${N}_$M.trace.txt
        # end the preview: the QA app's own saved look comes back (nothing was saved)
        # (the 3D switch is left as this run set it, in memory only: a restart of the QA app gives the build's own default)
        am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_preview false > /dev/null; top ;;
  pair) N=$1; S=${2:-6}; sh $0 run old $N $S && sh $0 run new $N $S ;;
  trace) N=$1; S=$2; logcat -c; sleep $S; logcat -d -s VoltcapQA > $OUT/$N.trace.txt; grep -cE "EMPTY|SWAP" $OUT/$N.trace.txt ;;
esac
