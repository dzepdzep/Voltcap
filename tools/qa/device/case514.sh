#!/system/bin/sh
# Update 514: wide / square / portrait covers on the 3D cases — isolated Voltcap QA app ONLY (fixture art, preview layouts,
# settings snapshotted and restored). sh case514.sh TAG [modes] [layouts]
#   per art mode: each case layout's home (and one D-pad step later), then fixture games held in Inspect at 4 angles,
#   and media_dump.txt (what each case is drawn from).
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; MODES=${2:-wide portrait square}; LAYS=${3:-SHELF STACKS COVERDETAILS CASEFLOW}
O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
k() { f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) input keyevent $1; sleep ${2:-0.8};; *) log "ABORT $f"; echo DONE >> $L; exit 2;; esac; }
log "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher)"
A --ez voltcap_test_nolaunch true; sleep 3
A --es voltcap_test_snapshot save; sleep 2; k 97 1
A --ez voltcap_test_preview true; sleep 1
for M in $MODES; do
  A --es voltcap_test_fixture "names=real,art=$M,meta=none"; sleep 3
  for LY in $LAYS; do
    A --es voltcap_test_layout $LY --es voltcap_test_game "Shadow of the Colossus"; sleep 4
    screencap -p $O/${M}_${LY}_0.png; k 22 2.2; screencap -p $O/${M}_${LY}_1.png; k 21 2.2
    log "lay $M $LY"
  done
  rm -f $F/media_dump.txt; A --es voltcap_test_mediadump "Shadow"; sleep 2; cp $F/media_dump.txt $O/${M}_media_dump.txt 2>/dev/null
  for g in "Shadow_of_the_Colossus" "Super_Mario_Sunshine" "Crash_Bandicoot" "Mario_Kart_64"; do
    for pose in "0|0" "35|8" "90|0" "25|40"; do
      k 97 0.6; A --es voltcap_test_inspect "$g||${pose%|*}|${pose#*|}"; sleep 3
      screencap -p "$O/${M}_insp_${g}_$(echo $pose | tr '|' '_').png"
    done
  done
  k 97 0.8
done
A --es voltcap_test_fixture off; sleep 1; A --ez voltcap_test_preview false; sleep 0.5
k 97 0.8; A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cat $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
log "launches=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked') end $(date +%T) real=$(pidof com.shelf.launcher)"; echo DONE >> $L
