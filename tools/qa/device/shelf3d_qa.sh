# Game shelf: OLD vs NEW (3D case) pull-out, artwork readiness and spine flicker - QA app only. 
# Runs on the handheld from /data/local/tmp. Same gates as ci_qa.sh: a failed gate stops the script with no input sent.
# Nothing is saved: the layout is a preview (voltcap_test_preview, ended at the end of each run), the 3D switch is in memory (Shelf3D.enabled), games never start
# (voltcap_test_nolaunch). The real Voltcap (com.shelf.launcher) is never launched, installed over, cleared or touched.
# usage:  sh shelf3d_qa.sh pre                         read-only: awake, unlocked, the real Voltcap NOT in front, QA app installed
#         sh shelf3d_qa.sh base                        the real Voltcap's identity (run before AND after: must match)
#         sh shelf3d_qa.sh run MODE NAME [STEPS]       MODE old|new: open the Game shelf in the QA app, record STEPS
#                                                      D-pad moves (default 6) at 0.9 s, then back and forth fast (3 × ◀▶)
#         sh shelf3d_qa.sh pair NAME [STEPS]           run old then new with the same game and moves (the comparison)
#         COLD=1 sh shelf3d_qa.sh ...                  stop the QA app first (cold artwork); the real Voltcap is never stopped
#         sh shelf3d_qa.sh trace NAME SECS             logcat of the QA trace (SpineTrace EMPTY / SWAP / REDECODE, hand-over)
P=com.shelf.launcher.qa; REAL=com.shelf.launcher; OUT=/sdcard/pc/shelf3d; mkdir -p $OUT
awake()  { dumpsys power | grep -q "mWakefulness=Awake"; }
locked() { dumpsys window | grep -qE "mDreamingLockscreen=true|isStatusBarKeyguard=true|mShowingLockscreen=true|isKeyguardShowing=true"; }
top()    { dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity"; }
gate_pre() {
  awake || { echo "STOP: screen asleep"; exit 3; }
  locked && { echo "STOP: locked"; exit 3; }
  top | grep -q "$REAL/" && { echo "STOP: real Voltcap in front ($(top))"; exit 3; }
  pm path $P > /dev/null || { echo "STOP: QA app not installed"; exit 3; }
  return 0; }
gate_qa() {
  awake || { echo "STOP: screen asleep"; exit 3; }
  locked && { echo "STOP: locked"; exit 3; }
  t=$(top); echo "$t" | grep -q "$P/" || { echo "STOP: QA app not in front ($t)"; exit 3; }
  echo "$t" | grep -q "$REAL/" && { echo "STOP: real Voltcap in front"; exit 3; }
  return 0; }
key() { gate_qa; input keyevent KEYCODE_$1 || { echo "STOP: input failed"; exit 4; }; }
open_shelf() {   # $1 = true|false (the 3D case). COLD=1: the QA app (never the real one) is stopped first: cold artwork in memory
  gate_pre
  [ "$COLD" = 1 ] && { am force-stop $P; sleep 1; }
  am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_preview true --es voltcap_test_layout SHELF --ez voltcap_test_nolaunch true \
    --ez voltcap_test_shelf3d $1 > /dev/null || { echo "STOP: start failed"; exit 4; }
  sleep 3; gate_qa; }
cmd=$1; shift
case $cmd in
  pre)  gate_pre; echo "PRE-OK $(getprop ro.product.model) $(wm size | tail -1) $(wm density | tail -1)"; top
        dumpsys package $P | grep -m2 -E "versionName|signatures" ;;
  base) dumpsys package $REAL | grep -m3 -E "versionName|versionCode|lastUpdateTime"; pm path $REAL ;;
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
