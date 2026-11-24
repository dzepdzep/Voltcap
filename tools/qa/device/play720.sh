#!/system/bin/sh
# 720a - HW-24 715a: the Voltcap row's added Play part moved with the REAL Edit Home D-pad, and where the home's ▲ / ▼ then go
# (LEVEL 1, isolated QA app com.shelf.launcher.qa ONLY). Game header off, "Play / Continue" added to the Voltcap row (CLASSIC),
# its place set just above the middle (y 0.44); then in Edit Home (`voltcap_test_edithome open` + `select:CLASSIC:h_play`) five
# real ▼ presses take it below the middle, Done saves it, and on the home a real ▼ must reach Play (▲ before, while it was above).
# Undo (Y) takes one step back, Discard keeps the saved place, a restart keeps it, "Reset this part" puts it back; then 715a's own
# case - the Row at the top (R1 Row tile handler, preview) with Play at its normal spot above: ▲ reaches it.
# Safety: every key only while the QA app is in front; a D-pad key in Edit Home only after voltcap_qa_edit reads
# "EDIT CLASSIC:h_play" (Edit Home open on that part); home keys only on "HOME"; never A (a game would start) - B leaves Play.
# Settings snapshot saved first and restored; the owner's Voltcap is never touched.
# Grade: tools/qa/tools/play720.py RUN_DIR
#   sh play720.sh TAG [font=1.3]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; SC=${2:-off}; O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
ID=CLASSIC:h_play
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
stop() { log "STOP $1"; echo DONE >> $L; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
picker() { grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { gate; input keyevent KEYCODE_BUTTON_B; sleep 1.5; log "picker dismissed"; }; }
mark() { cat $F/voltcap_qa_edit 2>/dev/null; }
# a real D-pad key inside Edit Home, only while it is open on the Play part
ek() { gate; [ "$(mark)" = "EDIT $ID" ] || stop "Edit Home not open on $ID (mark: $(mark)) - nothing pressed"; input keyevent KEYCODE_$1; sleep 0.5; }
# a real key on the home, only while Edit Home is closed
hk() { gate; [ "$(mark)" = "HOME" ] || stop "Edit Home still open (mark: $(mark)) - nothing pressed"; input keyevent KEYCODE_$1; sleep 0.8; }
E() { A --es voltcap_test_edithome "$1"; sleep 1.2; }
shot() { sleep 1; gate; screencap -p $O/$1.png; A --es voltcap_test_clip "$1"; sleep 1.3; cp "$F/qa_clip/$1.json" $O/ 2>/dev/null; }
# expect STEP WHAT: one reach line (WHAT = above | below | zone | live | none ... see play720.py)
R() { log "expect $1 $2"; A --ez voltcap_test_reach true; sleep 1.4; }
pm path $P >/dev/null 2>&1 || stop "$P not installed - nothing pressed"
touch $F/voltcap_test_hooks $F/qa_nolaunch
REAL=$(pidof com.shelf.launcher)
log "target: serial=$(getprop ro.serialno) model=$(getprop ro.product.model) package=$P $(dumpsys package $P | grep -m1 versionName | tr -d ' ') scale=$SC real_pid=${REAL:-none}"
logcat -c
A --ez voltcap_test_nolaunch true; sleep 3; gate; picker
A --es voltcap_test_snapshot save; sleep 2
A --es voltcap_test_fixture "names=real,art=portrait,meta=none"; sleep 1
A --es voltcap_test_scale "$SC"; sleep 1
A --es voltcap_test_layout CLASSIC --es voltcap_test_category ALL --es voltcap_test_game "Super Mario World"; sleep 4; gate; picker
A --es voltcap_test_pref "home_header=false"; A --es voltcap_test_pref "overlay_parts=CLASSIC=h_play"
A --es voltcap_test_pref "widget_positions=$ID=0.8000,0.4400"; sleep 2
[ "$(mark)" = "HOME" ] || stop "no voltcap_qa_edit mark (an old build without 720a?)"
R r1 above; shot r1_above
hk DPAD_UP; R r2 zone; shot r2_up_on_play; hk BUTTON_B
E open; E select:$ID; R r3 edit
for i in 1 2 3 4 5; do ek DPAD_DOWN; done
R r4 live+5; shot r4_moved_down
E save; R r5 below; shot r5_saved_below
hk DPAD_DOWN; R r6 zone; shot r6_down_on_play; hk BUTTON_B
E open; E select:$ID; ek DPAD_UP; ek DPAD_UP; ek BUTTON_Y; R r7 live-1; shot r7_undo
E discard; R r8 kept; shot r8_discarded
am force-stop $P; sleep 1; A; sleep 5; gate; picker; R r9 kept; shot r9_after_restart
E open; E reset:$ID; E save; R r10 reset; shot r10_reset
A --ez voltcap_test_preview true; sleep 1; A --es voltcap_test_dock TOP; sleep 2
R r11 above; hk DPAD_UP; R r12 zone; shot r12_top_up_on_play; hk BUTTON_B
A --es voltcap_test_scale off; A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
logcat -d -s VoltcapQA | grep -E " reach " > $O/reach.txt
log "real_after=$(pidof com.shelf.launcher) fatal=$(logcat -d | grep -c 'FATAL EXCEPTION')"
echo DONE >> $L
