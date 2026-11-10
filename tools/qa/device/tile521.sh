#!/system/bin/sh
# Batch 521 fix check: Tile home with the owner-like Edit home move (row -90 dp, buttons placed by the tiles), then the R1 Row
# place (same reset as the R1 tile) Top / Bottom / Middle; plus Top with no move (profiles). Isolated QA app ONLY; restored.
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
picker() { grep -q "^PICKER $(pidof $P)" $F/voltcap_qa_screen 2>/dev/null && { input keyevent 97; sleep 1.5; log "picker dismissed"; }; }
shot() { sleep 3; screencap -p $O/$1.png; log "$1 $(logcat -d -s VoltcapQA | grep tilemid | tail -1 | sed 's/.*tilemid//')"; }
log "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher)"; logcat -c
A --ez voltcap_test_nolaunch true; sleep 3; picker
A --es voltcap_test_snapshot save; sleep 2
A --ez voltcap_test_preview true; sleep 1
A --es voltcap_test_fixture "names=real,art=portrait,meta=none"; sleep 1
A --es voltcap_test_layout TILES --es voltcap_test_category ALL --es voltcap_test_game "Donkey Konga"; sleep 4; picker
A --es voltcap_test_dock MIDDLE; sleep 2
A --es voltcap_test_pref "part_shifts=part:TILES:row=0.0,-90.0"; A --es voltcap_test_pref "widget_positions=shortcuts=0.5000,0.7400"; shot moved_MIDDLE
A --es voltcap_test_dock TOP; shot r1_TOP
A --es voltcap_test_dock BOTTOM; shot r1_BOTTOM
A --es voltcap_test_dock MIDDLE; shot r1_MIDDLE
A --es voltcap_test_pref "part_shifts=part:TILES:row=0.0,-60.0"; shot moved60_MIDDLE_nochange
A --es voltcap_test_dock MIDDLE; shot r1_MIDDLE_again
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cat $F/voltcap_test_snapshot_verify.txt 2>/dev/null | cut -c1-40)"
log "real=$(pidof com.shelf.launcher) fatal=$(logcat -d | grep -c 'FATAL EXCEPTION')"; echo DONE >> $L
