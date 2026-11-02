# v0.6.66 D137: Case stacks frame pacing on the GT78 (no screenrecord; app-side QaTrace slow-frame lines, not dumpsys gfxinfo —
# `gfxinfo reset` aborted Voltcap once in Android 11's HWUI JankTracker on this device).
# usage: sh d137.sh TAG ["MODES"] [game] — modes: USER (your background, clips on), NOVIDEO (your background, clips off this session:
# voltcap_test_novideo), ANIMATED, BLACK. Each: 3 x (5 s idle, 6 moves 0.8 s apart). Full VoltcapQA log per run in
# /sdcard/d137/TAG_<mode>_<move|idle>_<r>.txt (MARK lines = each key) for per-move analysis.
# (D136: the "Who's playing?" profile picker can come up in front after a while — B keeps the current profile, nothing else.)
# (D136, update 452: the app's own screen mark first — uiautomator can't dump an animated home on the RP Classic, so the
# picker was missed and the moves walked it; the dump stays as the fallback for builds without the mark.)
picker() { m=/sdcard/Android/data/com.shelf.launcher/files/voltcap_qa_screen; if [ -s $m ] && grep -q "^PICKER $(pidof com.shelf.launcher | cut -d' ' -f1)" $m; then input keyevent KEYCODE_BUTTON_B; sleep 1.5; echo "picker dismissed (mark)"; elif [ ! -s $m ]; then uiautomator dump /sdcard/vqa_ui.xml > /dev/null 2>&1; grep -q "Who.s playing" /sdcard/vqa_ui.xml 2>/dev/null && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; echo "picker dismissed"; }; rm -f /sdcard/vqa_ui.xml; fi; }
cad() { grep cadence $1 | sed 's/.*n=\([0-9]*\) missed=\([0-9]*\).*/\1 \2/' | awk '{n+=$1; m+=$2} END {printf "frames=%d missed=%d", n, m}'; }
A() { am start -n com.shelf.launcher/.MainActivity "$@" > /dev/null 2>&1; }
cnt() { logcat -d -s VoltcapQA | grep " slow " | awk '{for(i=1;i<=NF;i++) if($i=="slow") print $(i+1)}' | awk -v t=$1 '$1>t{c++} END{print c+0}'; }
TAG=$1; MODES=${2:-"USER NOVIDEO ANIMATED"}; GAME=$3
D=/sdcard/d137; mkdir -p $D
for bg in $MODES; do
  A --ez voltcap_test_preview false; sleep 3; A --ez voltcap_test_preview true; sleep 1
  A --ez voltcap_test_novideo false
  case $bg in
    NOVIDEO) A --ez voltcap_test_novideo true;;
    ANIMATED|BLACK) A --es voltcap_test_bg $bg;;
  esac
  sleep 2
  A --es voltcap_test_layout STACKS; sleep 3
  for r in 1 2 3; do for m in idle move; do
    # (Each run from the same game, resting 9 s first so its clip plays: the move starts with the clip on, as you would.)
    [ -n "$GAME" ] && { A --es voltcap_test_game "$GAME"; sleep 9; }
    # (A key with no job: the home screen isn't idle, so a clip plays on — it holds its frame after 30 s without input.)
    picker
    input keyevent KEYCODE_SCROLL_LOCK; sleep 0.5
    logcat -c
    if [ $m = move ]; then for i in 1 2 3 4 5 6; do log -p e -t VoltcapQA "MARK mv $i"; input keyevent KEYCODE_DPAD_RIGHT; sleep 0.8; done; sleep 1; else sleep 5; fi
    logcat -d -s VoltcapQA > $D/${TAG}_${bg}_${m}_$r.txt
    echo "$TAG bg=$bg $m $r: over16=$(cnt 16.7) over33=$(cnt 33) $(cad $D/${TAG}_${bg}_${m}_$r.txt)"
  done; done
done
A --ez voltcap_test_novideo false
A --ez voltcap_test_preview false
echo DONE
