#!/system/bin/sh
# 529o - #15 queue item 1: after a tap inside the game window, R1 / L1 reached nothing (k529l3: no "winkey" at all). This
# records where the input focus is before and after a tap, and whether the next R1 reaches the window. Isolated QA app ONLY,
# fixture library, launches blocked, preview on; the owner's QA snapshot is kept and put back by `restore`.
#   sh touchfocus529.sh run TAG [LAYOUT]     (default SHELF)
#   sh touchfocus529.sh restore TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
LAY=${3:-SHELF}
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; echo DONE >> $L; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
k() { gate; input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
shot() { screencap -p $O/$1.png; echo "${TAG}_$1" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.1; }
focus() { log "$1: window=$(dumpsys window | grep -m1 mCurrentFocus | sed 's/.*Window{[0-9a-f]* u0 //;s/}.*//') input=$(dumpsys input | grep -m1 -iE 'FocusedWindow|focusedWindowHandle' | sed 's/^ *//' | cut -c1-120)"; }
ctl() { grep -oiE '"label":"([^"]*[^A-Za-z])?'"$1"'","kind":"control","full":\[[0-9,-]*\],"shown":\[[0-9-]*,[0-9-]*,[0-9]*,[0-9]*\]' $2 2>/dev/null; }
tapj() { r=$(ctl "$1" $F/qa_clip/${TAG}_$2.json | grep -vE '"shown":\[[0-9-]*,[0-9-]*,0,|"shown":\[[0-9-]*,[0-9-]*,[0-9]*,0\]' | head -1 | sed 's/.*"shown":\[//;s/\]//')
  [ -n "$r" ] || { log "tap: no shown control '$1' in $2"; return 1; }
  gate; set -- $(echo $r | tr ',' ' '); x=$(( $1 + $3 / 2 )); y=$(( $2 + $4 / 2 )); input tap $x $y; log "tap at $x,$y"; }
winkeys() { logcat -d -s VoltcapQA | grep -c "winkey"; }
launches() { logcat -d -s VoltcapQA | grep -c "LAUNCH blocked"; }
case $MODE in
run)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep"
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_B 1.5; log "picker closed with B"; }
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_artfix "delay=0"; sleep 3
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_game "Shadow of the Colossus"; sleep 2
  A --ez voltcap_test_preview true --es voltcap_test_pref "motion_level=NORMAL"; A --es voltcap_test_layout $LAY; sleep 3.5; gate
  k BUTTON_A 1.9; shot main; focus "opened"
  k BUTTON_R1 1.0; k BUTTON_R1 1.2; shot trophies; focus "achievements page"
  logcat -c; k BUTTON_R1 1.2; k BUTTON_L1 1.2; log "control keys before the tap reached the window: $(winkeys) (want 4: R1, L1 down/up)"; shot before_tap
  tapj "[^\"]*achievement 1[^\"]*" before_tap && { sleep 1.3; shot after_tap; focus "after tap"; }
  logcat -c; k BUTTON_R1 1.2; log "R1 after the tap reached the window: $(winkeys) key event(s)"; shot after_r1; focus "after R1"
  # 529p: A / Start after a tap only light a control - never a launch (qa_nolaunch logs a blocked launch).
  k BUTTON_L1 1.2; shot back_trophies; tapj "[^\"]*achievement 1[^\"]*" back_trophies && sleep 1.3
  logcat -c; k BUTTON_A 1.2; log "A after a tap: launches=$(launches) winkeys=$(winkeys)"; shot after_tap_a
  tapj "[^\"]*achievement 1[^\"]*" after_tap_a && sleep 1.3
  logcat -c; k BUTTON_START 1.2; log "Start after a tap: launches=$(launches) winkeys=$(winkeys)"; shot after_tap_start
  k BUTTON_R1 1.2
  logcat -c; k DPAD_DOWN 1.0; log "D-pad after the tap reached the window: $(winkeys)"; focus "after D-pad"
  logcat -c; k BUTTON_R1 1.2; log "R1 after the D-pad reached the window: $(winkeys)"; shot after_r1_2
  # 529o single B (#15 queue 2): Options page -> "Game settings" with A -> one B. The window must still be there: the next R1
  # reaches it (winkey) and the home screen's Shortcuts never open.
  shot back_main; focus "back on main (R1 from Options wraps to the first page)"
  k BUTTON_R1 1.1; k BUTTON_R1 1.1; k BUTTON_R1 1.3; shot options
  k DPAD_DOWN 0.8; shot options_gs; k BUTTON_A 1.8; shot sheet; focus "game settings sheet"
  k BUTTON_B 1.6; shot after_b; focus "after one B"
  logcat -c; k BUTTON_L1 1.3; log "L1 after the sheet's B reached the window: $(winkeys) (0 = the window was gone)"; shot after_b_l1
  log "screen mark: $(cut -c1-40 $F/voltcap_qa_screen 2>/dev/null)"
  # 529p: Game settings' Artwork button (a full screen) still closes the window under the sheet and opens the artwork screen.
  k BUTTON_R1 1.2; shot options2; k DPAD_DOWN 0.8; k BUTTON_A 1.8; shot sheet2
  tapj "Artwork[^\"]*" sheet2 && { sleep 2.2; shot artwork; focus "artwork screen"; log "screen mark after Artwork: $(cut -c1-40 $F/voltcap_qa_screen 2>/dev/null)"; }
  k BUTTON_B 1.6; shot after_artwork_b
  shot closed
  log "run done fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')"; echo DONE >> $L ;;
restore)
  A --es voltcap_test_pref "motion_level=NORMAL" --es voltcap_test_scale off --es voltcap_test_fixture off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  am force-stop $P; log "QA app stopped" ;;
esac
