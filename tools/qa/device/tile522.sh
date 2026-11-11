#!/system/bin/sh
# Batch 522: Tile home part independence - two parts moved (the tile row and the round shortcuts), then each reset on its own;
# the other must stay exactly where it was. QaClip dumps after every step (part boxes compared by tools/qa/tools/tile522.py),
# plus R1 Row TOP / BOTTOM / MIDDLE with both moved. Isolated QA app ONLY; QA settings snapshotted and restored.
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
picker() { grep -q "^PICKER $(pidof $P)" $F/voltcap_qa_screen 2>/dev/null && { input keyevent 97; sleep 1.5; log "picker dismissed"; }; }
shot() { sleep 3; screencap -p $O/$1.png; rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; sleep 1.3; cp $F/qa_clip/${TAG}_$1.json $O/$1.json 2>/dev/null; log "$1"; }
log "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher)"; logcat -c
A --ez voltcap_test_nolaunch true; sleep 3; picker
A --es voltcap_test_snapshot save; sleep 2
A --ez voltcap_test_preview true; sleep 1
A --es voltcap_test_fixture "names=real,art=portrait,meta=none"; sleep 1
A --es voltcap_test_layout TILES --es voltcap_test_category ALL --es voltcap_test_game "Super Mario World"; sleep 4; picker
A --es voltcap_test_dock MIDDLE; A --es voltcap_test_pref "part_shifts="; A --es voltcap_test_pref "widget_positions="; shot base
A --es voltcap_test_pref "part_shifts=part:TILES:row=0.0,-90.0"; shot row_moved
A --es voltcap_test_pref "widget_positions=shortcuts=0.5000,0.7400"; shot both_moved
A --es voltcap_test_pref "widget_positions="; shot shortcuts_reset
A --es voltcap_test_pref "widget_positions=shortcuts=0.5000,0.7400"; shot both_again
A --es voltcap_test_pref "part_shifts="; shot row_reset
A --es voltcap_test_pref "part_shifts=part:TILES:row=0.0,-90.0"
for D in TOP BOTTOM MIDDLE; do A --es voltcap_test_dock $D; shot r1_$D; done
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
log "real=$(pidof com.shelf.launcher) fatal=$(logcat -d | grep -c 'FATAL EXCEPTION')"; echo DONE >> $L
