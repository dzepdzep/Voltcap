#!/system/bin/sh
# Batch 522: popup + controller-glyph sweep on the isolated QA app ONLY (popclip.sh, 53 layouts x popup styles, QaClip dumps with
# the InkGlyph boxes). QA settings snapshotted before and restored after; optional QA-only scale (voltcap_test_scale, never the
# system font_scale / density).  sh pop522.sh TAG "LAYOUTS" "STYLES" "GAME" [SCALE e.g. font=1.3,density=1]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; SC=$5; D=/data/local/tmp
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
mkdir -p /sdcard/pc; W=/sdcard/pc/${TAG}_wrap.txt; echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $W
input keyevent 224; A --ez voltcap_test_nolaunch true; sleep 3
grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { input keyevent 97; sleep 1.5; }
A --es voltcap_test_snapshot save; sleep 2
[ -n "$SC" ] && { A --es voltcap_test_scale "$SC"; sleep 2; echo "scale $SC" >> $W; }
sh $D/popclip.sh "$TAG" "$2" "$3" "$4"
[ -n "$SC" ] && { A --es voltcap_test_scale off; sleep 1; }
A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cut -c1-30 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)" >> $W
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $W; echo DONE >> $W
