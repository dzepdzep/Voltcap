#!/system/bin/sh
# Batch 520: Game shelf pulled-out case with wide / portrait fixture art — isolated Voltcap QA app ONLY (fixture art,
# preview layout, settings snapshotted and restored). sh q520.sh TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
k() { f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) input keyevent $1; sleep ${2:-0.8};; *) log "ABORT $f"; echo DONE >> $L; exit 2;; esac; }
log "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher) qa=$(dumpsys package $P | grep -m1 versionName)"
input keyevent 224; A --ez voltcap_test_nolaunch true; sleep 4; k 97 1.5
A --es voltcap_test_snapshot save; sleep 2; k 97 1
A --ez voltcap_test_preview true; sleep 1
for M in wide portrait; do
  A --es voltcap_test_fixture "names=real,art=$M,meta=none"; sleep 3
  for PU in COVER OURS FRONT; do
    A --es voltcap_test_pref "shelf_pulled=$PU"; sleep 1
    for G in "Shadow of the Colossus" "Super Mario Sunshine" "Crash Bandicoot"; do
      A --es voltcap_test_layout SHELF --es voltcap_test_game "$G"; sleep 4
      N=$(echo "${M}_${PU}_$G" | tr ' ' '_'); screencap -p $O/$N.png; log "shot $N"
    done
  done
done
A --es voltcap_test_fixture off; sleep 1; A --ez voltcap_test_preview false; sleep 0.5
k 97 0.8; A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cat $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
log "launches=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked') end $(date +%T) real=$(pidof com.shelf.launcher)"; echo DONE >> $L
