#!/system/bin/sh
# 719a - HW-24 716a in its real combination (LEVEL 1, isolated QA app com.shelf.launcher.qa ONLY): the Voltcap row (Classic)
# with the game header + Game info / news + the category bar of every category + a song in the status bar's music player
# (`--es voltcap_test_rowcombo on`, RowCombo719 - a preview, nothing saved), Row place Middle -> Bottom -> Top through the R1
# Row tile's own handler (`voltcap_test_dock`, rowTilePress), each graded on one `rowcol ...` line (`voltcap_test_rowcol`):
# the column never starts under the status bar. First the same three places with the defaults (baseline). The QA app's
# settings saved first (snapshot) and restored. No key / tap except B on the first-start picker, only while the QA app is in front.
# Grade: tools/qa/tools/row716.py RUN_DIR
#   sh row716.sh TAG [font=1.3]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; SC=${2:-off}; O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
stop() { log "STOP $1"; echo DONE >> $L; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
picker() { grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { gate; input keyevent KEYCODE_BUTTON_B; sleep 1.5; log "picker dismissed"; }; }
shot() { sleep 2; gate; screencap -p $O/$1.png; A --es voltcap_test_clip "$1"; sleep 1.3; cp "$F/qa_clip/$1.json" $O/ 2>/dev/null; }
# expect STEP PLACE COMBO, then the Row tile's change to PLACE, one rowcol line, a screenshot + layout dump
place() { log "expect $1 $2 $3"; A --es voltcap_test_dock $2; sleep 2; A --ez voltcap_test_rowcol true; shot "$1_$2_$3"; }
pm path $P >/dev/null 2>&1 || stop "$P not installed - nothing pressed"
touch $F/voltcap_test_hooks $F/qa_nolaunch
REAL=$(pidof com.shelf.launcher)
log "target: serial=$(getprop ro.serialno) model=$(getprop ro.product.model) package=$P $(dumpsys package $P | grep -m1 versionName | tr -d ' ') scale=$SC real_pid=${REAL:-none}"
logcat -c
A --ez voltcap_test_nolaunch true; sleep 3; gate; picker
A --es voltcap_test_snapshot save; sleep 2
A --ez voltcap_test_preview true; sleep 1
A --es voltcap_test_fixture "names=real,art=portrait,meta=none"; sleep 1
A --es voltcap_test_scale "$SC"; sleep 1
A --es voltcap_test_layout CLASSIC --es voltcap_test_category ALL --es voltcap_test_game "Super Mario World"; sleep 4; gate; picker
place b1 MIDDLE off
place b2 BOTTOM off
place b3 TOP off
A --es voltcap_test_rowcombo on; sleep 2
place c1 MIDDLE on
place c2 BOTTOM on
place c3 TOP on
place c4 MIDDLE on
A --es voltcap_test_rowcombo off; sleep 1
A --es voltcap_test_scale off; A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
logcat -d -s VoltcapQA | grep -E " (rowcol|rowcombo) " > $O/rowcol.txt
logcat -d -s VoltcapQA | grep " setting " > $O/settings.txt
log "real_after=$(pidof com.shelf.launcher) fatal=$(logcat -d | grep -c 'FATAL EXCEPTION')"
echo DONE >> $L
