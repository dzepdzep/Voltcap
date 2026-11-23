#!/system/bin/sh
# 717a - HW-24 714a without the R1 panel (LEVEL 1, isolated QA app com.shelf.launcher.qa ONLY). R1 opens on its Quick row
# (Wi-Fi, Bluetooth, Do not disturb...), so no script opens it: the Row tile's own change (tileRowPlaceReset + withDockHere)
# runs through `--es voltcap_test_dock`, which logs `rowtile ...` (rowTileQaLine). Tile home, the round buttons placed by the
# tiles at Middle, then Top -> Middle -> Bottom, placed again at Bottom, Top -> Bottom -> Middle -> Middle: each place gets its
# own spot back, the others stay put aside. Preview session; the QA app's settings saved first (snapshot) and restored.
# No key / tap except B on the first-start picker, and only while the QA app is in front. Grade: tools/qa/tools/row714.py RUN_DIR
#   sh row714.sh TAG [font=1.3]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; SC=${2:-off}; O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
stop() { log "STOP $1"; echo DONE >> $L; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
picker() { grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { gate; input keyevent KEYCODE_BUTTON_B; sleep 1.5; log "picker dismissed"; }; }
shot() { sleep 2; gate; screencap -p $O/$1.png; A --es voltcap_test_clip "$1"; sleep 1.3; cp "$F/qa_clip/$1.json" $O/ 2>/dev/null; }
# expect STEP SHORTCUTS KEPT, then the Row tile's change to PLACE
dock() { log "expect $1 $3 $4"; A --es voltcap_test_dock $2; shot "$1_$2"; }
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
A --es voltcap_test_layout TILES --es voltcap_test_category ALL --es voltcap_test_game "Super Mario World"; sleep 4; gate; picker
dock s00 MIDDLE any any
A --es voltcap_test_pref "widget_positions=shortcuts=0.3000,0.2000"; shot s0_placed_MIDDLE
dock s1 TOP none MIDDLE:0.3000,0.2000
dock s2 MIDDLE 0.3000,0.2000 -
dock s3 BOTTOM none MIDDLE:0.3000,0.2000
A --es voltcap_test_pref "widget_positions=shortcuts=0.7000,0.7000;shortcuts@MIDDLE=0.3000,0.2000"; shot s3b_placed_BOTTOM
dock s4 TOP none "MIDDLE:0.3000,0.2000;BOTTOM:0.7000,0.7000"
dock s5 BOTTOM 0.7000,0.7000 MIDDLE:0.3000,0.2000
dock s6 MIDDLE 0.3000,0.2000 BOTTOM:0.7000,0.7000
dock s7 MIDDLE 0.3000,0.2000 BOTTOM:0.7000,0.7000
A --es voltcap_test_scale off; A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
logcat -d -s VoltcapQA | grep " rowtile " > $O/rowtile.txt
logcat -d -s VoltcapQA | grep " setting " > $O/settings.txt
log "real_after=$(pidof com.shelf.launcher) fatal=$(logcat -d | grep -c 'FATAL EXCEPTION')"
echo DONE >> $L
