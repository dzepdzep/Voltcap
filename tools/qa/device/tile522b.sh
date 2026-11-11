#!/system/bin/sh
# Batch 522b: Tile home independence through Edit Home's own actions (voltcap_test_edithome): moved row + moved round buttons,
# Edit Home open (the layout must not move), Reset this part (buttons, then row), Reset this layout, Save, and R1 Row
# Top / Bottom / Middle - a QaClip dump + screenshot per step (tools/qa/tools/tile522.py compares the parts' boxes).
# Isolated QA app ONLY; preview on (nothing saved); QA settings snapshotted and restored.  sh tile522b.sh TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
picker() { grep -q "^PICKER $(pidof $P)" $F/voltcap_qa_screen 2>/dev/null && { input keyevent 97; sleep 1.5; log "picker dismissed"; }; }
shot() { sleep 3; screencap -p $O/$1.png; rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; sleep 1.3; cp $F/qa_clip/${TAG}_$1.json $O/$1.json 2>/dev/null; log "$1"; }
E() { A --es voltcap_test_edithome "$1"; sleep 1; }
ROW=part:TILES:row
log "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher)"; logcat -c
A --ez voltcap_test_nolaunch true; sleep 3; picker
A --es voltcap_test_snapshot save; sleep 2
A --ez voltcap_test_preview true; sleep 1
A --es voltcap_test_fixture "names=real,art=portrait,meta=none"; sleep 1
A --es voltcap_test_layout TILES --es voltcap_test_category ALL --es voltcap_test_game "Super Mario World"; sleep 4; picker
for D in MIDDLE TOP BOTTOM; do
  A --es voltcap_test_dock $D; A --es voltcap_test_pref "part_shifts="; A --es voltcap_test_pref "widget_positions="; shot ${D}_base
  A --es voltcap_test_pref "widget_positions=shortcuts=0.5000,0.8600"; shot ${D}_buttons_moved
  A --es voltcap_test_pref "part_shifts=$ROW=0.0,-40.0"; shot ${D}_both_moved
  E open; shot ${D}_edit_open
  E reset:shortcuts; shot ${D}_edit_reset_buttons
  E save; shot ${D}_saved_buttons_reset
  A --es voltcap_test_pref "widget_positions=shortcuts=0.5000,0.8600"; sleep 1
  E open; E reset:$ROW; E save; shot ${D}_saved_row_reset
  A --es voltcap_test_pref "part_shifts=$ROW=0.0,-40.0"; sleep 1
  E open; E resetlayout:TILES; E save; shot ${D}_saved_layout_reset
done
A --es voltcap_test_pref "part_shifts="; A --es voltcap_test_pref "widget_positions="; A --es voltcap_test_dock MIDDLE
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
log "edithome log: $(logcat -d -s VoltcapTest | grep -c 'edithome')"
log "real=$(pidof com.shelf.launcher) fatal=$(logcat -d | grep -c 'FATAL EXCEPTION')"; echo DONE >> $L
