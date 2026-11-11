#!/system/bin/sh
# Batch 521: button letters in their circles - Who's playing picker, Spotlight / Memory card / Card binder / Pocket Quest /
# Cube hints, Platform browser legend. Isolated QA app ONLY (preview layouts; settings snapshotted + restored).
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
k() { f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) input keyevent $1; sleep ${2:-1};; *) log "ABORT $f"; echo DONE >> $L; exit 2;; esac; }
log "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher)"; logcat -c
input keyevent 224; am force-stop $P; sleep 1; A --ez voltcap_test_nolaunch true; sleep 5
screencap -p $O/picker.png; log "picker: $(cut -c1-12 $F/voltcap_qa_screen)"
grep -q "^PICKER" $F/voltcap_qa_screen && k 97 1.5
A --es voltcap_test_snapshot save; sleep 2
A --ez voltcap_test_preview true; sleep 1
A --es voltcap_test_fixture "names=real,art=portrait,meta=none"; sleep 1
for LY in SPOT MEMCARD CARDS POCKET CUBE; do
  A --es voltcap_test_layout $LY --es voltcap_test_category ALL; sleep 4; screencap -p $O/$LY.png; log "$LY shot"
done
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cut -c1-20 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
log "real=$(pidof com.shelf.launcher) fatal=$(logcat -d | grep -c 'FATAL EXCEPTION')"; echo DONE >> $L
