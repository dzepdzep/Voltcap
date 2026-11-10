#!/system/bin/sh
# Batch 520: KONKR (or any handheld) memory soak — isolated Voltcap QA app ONLY (launches blocked, preview layouts, snapshot saved
# and restored). Separates caches from leaks: meminfo per round, after 60 s idle and after a critical trim.
#   sh mem520.sh TAG [ROUNDS=10] [LAYOUTS="SHELF STACKS TILES CHAN CASEFLOW COVERDETAILS GRID HERO_TILES CINEMA BOX"]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; N=${2:-10}
LAYS=${3:-"SHELF STACKS TILES CHAN CASEFLOW COVERDETAILS GRID HERO_TILES CINEMA BOX"}
O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
k() { f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) input keyevent $1; sleep ${2:-0.35};; *) log "ABORT $f"; echo DONE >> $L; exit 2;; esac; }
mem() { dumpsys meminfo $P | awk -v t="$1" '/TOTAL PSS:/{pss=$3} /Java Heap:/{j=$3} /Native Heap:/{n=$3} /Graphics:/{g=$2} /EGL mtrack/{e=$3} /GL mtrack/{gl=$3} END{print t, "pss="pss, "java="j, "native="n, "graphics="g, "egl="e, "gl="gl}' >> $L; }
picker() { grep -q "^PICKER $(pidof $P)" $F/voltcap_qa_screen 2>/dev/null && { k 97 1.5; }; }
log "start $(date +%T) $(getprop ro.product.model) serial=$(getprop ro.serialno) real=$(pidof com.shelf.launcher)"
input keyevent 224; am force-stop $P; sleep 1; A --ez voltcap_test_nolaunch true; sleep 6; picker; A --es voltcap_test_snapshot save; sleep 2; k 97 1
A --ez voltcap_test_preview true; sleep 30; mem "cold+30s"
r=1; while [ $r -le $N ]; do
  for LY in $LAYS; do
    input keyevent 224; A --es voltcap_test_layout $LY --es voltcap_test_game "Mario Kart 64"; sleep 2.5; picker
    i=0; while [ $i -lt 10 ]; do k 22; i=$((i+1)); done; i=0; while [ $i -lt 10 ]; do k 21; i=$((i+1)); done
    k 96 1.2; k 97 1.0
  done
  mem "round$r"; r=$((r+1))
done
sleep 60; mem "idle60"
am send-trim-memory $P RUNNING_CRITICAL; sleep 5; mem "trim"
# (No HOME press: on the KONKR the real Voltcap is the home app — it must not be brought up by a test.)
A --ez voltcap_test_preview false; sleep 1; picker; k 97 0.8; A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cat $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
log "launchesBlocked=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked') fatal=$(logcat -d | grep -c 'FATAL EXCEPTION') real=$(pidof com.shelf.launcher)"; echo DONE >> $L
