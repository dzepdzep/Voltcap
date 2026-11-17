#!/system/bin/sh
# 678o (live KONKR session; HW-2 - the owner's Record crate checklist, a v0.6.73 gate): Record crate's game window at x1.0 (one
# row) and x1.3 (the approved vertical menu). Per scale: A opens it; the D-pad walks the keys one press each until MORE has the
# focus (a dump + screenshot every step: one size for every key, no size change while the focus moves); A on MORE opens its
# page and B closes it (the focus must come back to MORE); B closes the window; then 5 rounds of A B A B at 250 ms (nothing
# started, nothing left open). Isolated QA app ONLY (com.shelf.launcher.qa): keys only while it has the focus (else the QA app
# is stopped and the run ends, nothing pressed); launches blocked (qa_nolaunch); its settings snapshot saved and restored.
#   sh crate678.sh TAG ["off font=1.3,density=1.15"]   -> /sdcard/pc/TAG/; grade: python3 tools/qa/tools/crate678.py RUN_DIR
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; SCALES=${2:-off font=1.3,density=1.15}
OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT; L=$OUT/log.txt; rm -f $F/qa_clip/${TAG}_*
log() { echo "$(date +%T) $*" >> $L; }
. "$(dirname "$0")/evidence.sh" 2>/dev/null || . /data/local/tmp/evidence.sh
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" > /dev/null 2>&1; }
dumpsys power | grep -q "mWakefulness=Awake" || { log "STOP asleep - not woken"; echo DONE >> $L; exit 3; }
dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && { log "STOP locked"; echo DONE >> $L; exit 3; }
pm path $P > /dev/null 2>&1 || { log "STOP: $P not installed - nothing pressed"; echo DONE >> $L; exit 1; }
ev_start $OUT $TAG "scales=$SCALES" "layout=CRATE" "script_sha256=$(sha256sum "$0" | cut -d' ' -f1)"
echo "target: serial=$(getprop ro.serialno) model=$(getprop ro.product.model) package=$P $(dumpsys package $P | grep -m1 versionName | tr -d ' ')" >> $L
# (D334: the launch-block file before any key - the QA app's Back at its root stays in front, and Play starts nothing.)
touch $F/qa_nolaunch $F/voltcap_test_hooks
fg() { f=$(dumpsys window | grep mCurrentFocus | tail -1)
  case "$f" in *com.shelf.launcher.qa*) return 0;; esac
  log "ABORT focus=$f - nothing pressed"; am force-stop $P; ev_end $OUT; echo DONE >> $L; exit 2; }
k() { fg; input keyevent KEYCODE_$1; sleep ${2:-0.9}; }
# dump NAME: the QaClip layout dump of this moment (a request file the QA app reads - 523w: world-readable)
dump() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; chmod 666 $F/qa_clip_req
  i=0; while [ $i -lt 25 ] && [ ! -s $F/qa_clip/${TAG}_$1.json ]; do sleep 0.3; i=$((i+1)); done
  if [ -s $F/qa_clip/${TAG}_$1.json ]; then cp $F/qa_clip/${TAG}_$1.json $OUT/$1.json; else log "$1 NO DUMP"; fi; }
shot() { fg; screencap -p $OUT/$1.png; dump $1; }
# the focused control's label in the focused window of dump NAME (minified JSON: one window per line, one node per line)
focus_of() { sed 's/{"id":"/\n{"id":"/g' $OUT/$1.json 2>/dev/null | grep '"focus":true,"nodes"' | tr '{' '\n' | grep '"kind":"control"' \
  | grep '"focused":true' | grep -o '"label":"[^"]*"' | tail -1 | sed 's/"label":"//; s/"$//'; }
isup() { dump $1; grep -qE '"popupAgeMs": *[0-9]' $OUT/$1.json 2>/dev/null; }
A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 4
A --es voltcap_test_snapshot save; sleep 2
A --es voltcap_test_artfix "delay=0"; sleep 3
A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
for SC in $SCALES; do
  case $SC in off) S=X10; MV=DPAD_RIGHT;; *) S=X13; MV=DPAD_DOWN;; esac
  A --es voltcap_test_scale "$SC"; sleep 2
  A --es voltcap_test_layout CRATE --ez voltcap_test_preview true --es voltcap_test_game "Shadow of the Colossus"; sleep 4
  log "== $S ($SC) move=$MV"
  shot ${S}_home
  k BUTTON_A 1.8; shot ${S}_k0; log "${S}_k0 focus=$(focus_of ${S}_k0)"
  n=0; f=$(focus_of ${S}_k0)
  while [ $n -lt 6 ]; do
    case "$f" in [Mm][Oo][Rr][Ee]) break;; esac
    n=$((n+1)); k $MV 0.9; shot ${S}_k$n; f=$(focus_of ${S}_k$n); log "${S}_k$n focus=$f"
  done
  case "$f" in [Mm][Oo][Rr][Ee])
    k BUTTON_A 1.4; shot ${S}_moreopen; log "${S}_moreopen focus=$(focus_of ${S}_moreopen)"
    k BUTTON_B 1.4; shot ${S}_moreback; log "${S}_moreback focus=$(focus_of ${S}_moreback)";;
  *) log "${S} MORE not reached in $n presses (focus=$f)";; esac
  # B closes the window (once more only if it is still up)
  isup ${S}_probe1 && k BUTTON_B 1.4; isup ${S}_probe2 && { log "${S} a second B was needed"; k BUTTON_B 1.4; }
  shot ${S}_closed
  # rapid A B A B at 250 ms, 5 rounds: nothing started (launches blocked + counted), nothing left open
  logcat -c
  r=1; while [ $r -le 5 ]; do
    fg; input keyevent KEYCODE_BUTTON_A; sleep 0.25; fg; input keyevent KEYCODE_BUTTON_B; sleep 0.25
    fg; input keyevent KEYCODE_BUTTON_A; sleep 0.25; fg; input keyevent KEYCODE_BUTTON_B; sleep 1.6
    if isup ${S}_rapid$r; then log "${S} rapid $r: a window was left open"; echo "${S} rapid$r open" >> $OUT/rapid.txt; k BUTTON_B 1.4
    else echo "${S} rapid$r closed" >> $OUT/rapid.txt; fi
    # (the next round starts from the home as it was: the layout hook closes anything left - a B on the home opens the
    # category chooser - and puts the focus back on the game)
    A --es voltcap_test_layout CRATE --ez voltcap_test_preview true --es voltcap_test_game "Shadow of the Colossus"; sleep 2.5
    r=$((r+1))
  done
  echo "${S} launches=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')" >> $OUT/rapid.txt
done
A --es voltcap_test_scale off --es voltcap_test_fixture off --es voltcap_test_artfix off; sleep 1.5
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"; log "fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')"
ev_end $OUT; echo DONE >> $L
