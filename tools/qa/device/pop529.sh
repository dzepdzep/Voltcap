#!/system/bin/sh
# Update 529 (master sprint, Phase 4): popup regression on the final source - isolated QA app ONLY (com.shelf.launcher.qa).
# Never wakes / unlocks the handheld; keys only while the QA app is in front (popclip.sh's own guard). Launches blocked.
#   sh pop529.sh run TAG SCALE "LAYOUTS" "STYLES" [GAME]   setup (owner snapshot kept, fixture library + art + achievements, real
#                                                        covers where a name matches; SCALE: off | font=1.3,density=1.15 as the 523z gates),
#                                                        then popclip.sh TAG LAYOUTS STYLES GAME (setsid, detached; DONE in its log)
#   sh pop529.sh restore TAG                             fixture / scale off, preview off, owner snapshot back, compared
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/${TAG}_w; mkdir -p $O; L=$O/log.txt
D=$(dirname "$0")
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
case $MODE in
run)
  SC=$3; LAYS=$4; STYLES=$5; GAME=${6:-Shadow of the Colossus}
  dumpsys power | grep -q "mWakefulness=Awake" || { log "STOP asleep - not woken"; exit 3; }
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && { log "STOP locked"; exit 3; }
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5
  A --es voltcap_test_snapshot save; sleep 2
  # (as the 523z gates: fixture art + achievements (artfix), then the real covers QA pushed (art=covers) where a name matches)
  A --es voltcap_test_artfix "delay=0"; sleep 3
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  log "setup done scale=$SC game=$GAME layouts=$(echo $LAYS | wc -w) styles=$STYLES"
  POP_EXTRA=1 QA_CONTENT="real-covers scale=$SC" setsid sh $D/popclip.sh "$TAG" "$LAYS" "$STYLES" "$GAME" > /dev/null 2>&1 < /dev/null &
  log "popclip started" ;;
restore)
  A --es voltcap_test_scale off --es voltcap_test_fixture off --es voltcap_test_artfix off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  log "fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" ;;
esac
