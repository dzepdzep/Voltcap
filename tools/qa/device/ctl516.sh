#!/system/bin/sh
# Update 516: controller pass on a compact screen — isolated Voltcap QA app ONLY (launches blocked, preview layouts, settings
# snapshotted and restored). Per layout: D-pad 4 right / 2 down, R1 (notifications panel), L1 (shortcuts), R2 / L2 (category
# forward and back), Select (main menu, 3 down), Start (game options); after each a screenshot and the focused window, then B
# (twice for the menu) and a check that the home window has the focus again.  sh ctl516.sh TAG [LAYOUTS]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; LAYS=${2:-SHELF TILES CHAN STACKS}; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" | tail -1; }
log() { echo "$*" >> $L; }
k() { f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) ;; *) log "ABORT: focus $f"; echo DONE >> $L; exit 2;; esac; input keyevent $1; sleep ${2:-0.8}; }
picker() { grep -q "^PICKER $(pidof $P)" $F/voltcap_qa_screen 2>/dev/null && { k 97 1.5; log "picker dismissed"; }; }
log "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher)"; logcat -c
A --ez voltcap_test_nolaunch true; sleep 3; picker; A --es voltcap_test_snapshot save; sleep 2; k 97 1
A --ez voltcap_test_preview true; sleep 1
for LY in $LAYS; do
  A --es voltcap_test_layout $LY --es voltcap_test_game "Mario Kart 64"; sleep 3.5; picker; H=$(W)
  k 22 0.5; k 22 0.5; k 22 0.5; k 22 1.0; k 20 0.5; k 20 1.2; screencap -p $O/${LY}_dpad.png; k 19 0.5; k 19 0.8
  for kk in "103 R1" "102 L1" "109 SELECT" "108 START"; do set -- $kk
    k $1 1.6; W1=$(W); screencap -p $O/${LY}_$2.png
    [ $2 = SELECT ] && { k 20 0.4; k 20 0.4; k 20 1.0; screencap -p $O/${LY}_${2}_down.png; }
    k 97 1.2; [ "$(W)" != "$H" ] && k 97 1.2; W2=$(W)
    log "$LY $2 opened=$([ "$W1" != "$H" ] && echo window || echo inline) back=$([ "$W2" = "$H" ] && echo yes || echo NO)"
  done
  k 105 1.4; screencap -p $O/${LY}_R2.png; k 104 1.4; screencap -p $O/${LY}_L2.png; log "$LY R2/L2 focus=$([ "$(W)" = "$H" ] && echo home || echo OTHER)"
done
A --ez voltcap_test_preview false; sleep 0.5; k 97 0.8; A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cat $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
log "launchesBlocked=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked') fatal=$(logcat -d | grep -c 'FATAL EXCEPTION') real=$(pidof com.shelf.launcher)"; echo DONE >> $L
