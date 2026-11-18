#!/system/bin/sh
# 682e (owner 18 Nov, system rule: the home's Play button and the selected category chip share one anchor): before / after of
# every home layout, from ONE build - the rule off (voltcap_test_anchor_off true, QA app only) then on. LEVEL 1, the isolated
# QA app only (com.shelf.launcher.qa): intents, QaClip dumps and screenshots only - no key, no tap. Graded on the PC by
# tools/qa/tools/anchorcheck.py (the "on" dumps PASS; the "off" ones show the old gaps).
#   sh anchors682.sh run TAG "LAYOUTS" "CATS"     CATS: categories to show, e.g. "ALL PS2 Xbox360" (a console's name as on the
#                                                 home; ALL = All) - one early, one in the middle, one at the end of the bar
#   sh anchors682.sh restore TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
LAYS=${3:-CLASSIC}; CATS=${4:-ALL}
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; echo DONE >> $L; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
# (the 60 s screen timeout on the KONKR: a lone SHIFT to the QA app counts as activity - only while gate holds)
keep() { gate; input keyevent KEYCODE_SHIFT_LEFT; }
dr() { [ -s "$1" ] && [ "$(tail -c 1 "$1" 2>/dev/null)" = "}" ]; }
shot() { keep; rm -f $F/qa_clip/${TAG}_$1.json; screencap -p $O/$1.png; echo "${TAG}_$1" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.1
  i=0; while ! dr $F/qa_clip/${TAG}_$1.json && [ $i -lt 40 ]; do sleep 0.2; i=$((i + 1)); done
  dr $F/qa_clip/${TAG}_$1.json && cp $F/qa_clip/${TAG}_$1.json $O/$1.json || log "dump missing: $1"; }
case $MODE in
run)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; log "picker closed with B"; }
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  log "setup layouts=$LAYS cats=$CATS"
  for lay in $LAYS; do
    A --es voltcap_test_layout $lay; sleep 3; keep
    for c in $CATS; do
      for mode in off on; do
        if [ $mode = off ]; then A --ez voltcap_test_anchor_off true; else A --ez voltcap_test_anchor_off false; fi
        A --es voltcap_test_category "$c"; sleep 2.2
        shot ${lay}_${c}_$mode
      done
    done
    log "$lay done"
  done
  A --ez voltcap_test_anchor_off false
  log "run done fatal=$(logcat -d -b crash | grep -c 'Process: com.shelf.launcher.qa')"; echo DONE >> $L ;;
restore)
  A --ez voltcap_test_anchor_off false --es voltcap_test_category ALL --es voltcap_test_fixture off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/ 2>/dev/null; fi
  am force-stop $P; log "QA app stopped" ;;
esac
