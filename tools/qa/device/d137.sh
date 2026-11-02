# v0.6.66 D137: Case stacks frame pacing on the GT78 (no screenrecord; app-side QaTrace slow-frame lines, not dumpsys gfxinfo —
# `gfxinfo reset` aborted Voltcap once in Android 11's HWUI JankTracker on this device).
A() { am start -n com.shelf.launcher/.MainActivity "$@" > /dev/null 2>&1; }
cnt() { logcat -d -s VoltcapQA | grep " slow " | awk '{for(i=1;i<=NF;i++) if($i=="slow") print $(i+1)}' | awk -v t=$1 '$1>t{c++} END{print c+0}'; }
TAG=$1
for bg in USER ANIMATED; do
  A --ez voltcap_test_preview false; sleep 3; A --ez voltcap_test_preview true; sleep 1
  [ $bg = ANIMATED ] && { A --es voltcap_test_bg ANIMATED; sleep 2; }
  A --es voltcap_test_layout STACKS; sleep 7
  for r in 1 2 3; do for m in idle move; do
    logcat -c
    if [ $m = move ]; then for i in 1 2 3 4 5 6; do input keyevent KEYCODE_DPAD_RIGHT; sleep 0.8; done; sleep 1; else sleep 5; fi
    echo "$TAG bg=$bg $m $r: over16=$(cnt 16.7) over33=$(cnt 33)"
  done; done
done
A --ez voltcap_test_preview false
echo DONE
