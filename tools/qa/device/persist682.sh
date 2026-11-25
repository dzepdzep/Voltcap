#!/system/bin/sh
# 682bd (owner 25 Nov 21:24 - "verify that each setting survives navigation, restart, rescan, layout changes ... without
# modifying unrelated settings"): LEVEL 1, the isolated QA app com.shelf.launcher.qa only, synthetic games, nothing launched.
# After each operation a copy of the QA app's settings files (its own dump, voltcap_test_prefsdump) goes to
# /sdcard/pc/$TAG/snap_<n>.json; tools/qa/tools/persistdiff.py lists every key that changed between two copies.
#   sh persist682.sh run TAG          the walk below (about 4 minutes)
#   sh persist682.sh snap TAG NAME    one copy
# Every key a step changes on purpose is put back by the step itself; the QA snapshot hook covers the rest.
. "$(dirname "$0")/qaguard.sh" 2>/dev/null || . /data/local/tmp/vqa/qaguard.sh 2>/dev/null || { echo "qaguard.sh missing" >&2; exit 3; }
P=com.shelf.launcher.qa; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
log() { echo "$(date +%T) $*" >> $L; }
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
front() { dumpsys window | grep mCurrentFocus | grep -q "$P/"; }
stop() { log "STOP $*"; echo DONE >> $L; exit 4; }
# (The QA app is not debuggable - no run-as: it writes its own copy, QaPrefsDump, voltcap_test_prefsdump.)
snap() { F=/sdcard/Android/data/$P/files/prefsdump; rm -f $F/$1.json; A --es voltcap_test_prefsdump "$1"; i=0
  while [ ! -s $F/$1.json ] && [ $i -lt 20 ]; do sleep 0.5; i=$((i+1)); done
  cp $F/$1.json $O/snap_$1.json 2>/dev/null; log "snap $1 $(wc -c < $O/snap_$1.json 2>/dev/null || echo 0) bytes"; }
case "$MODE" in
snap) snap "$3" ;;
run)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep"
  front || stop "QA app not in front - nothing pressed"
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 4
  snap 0_start
  # 1. restart: the QA app's process ends and starts again (force-stop of the QA app only)
  am force-stop $P; sleep 2; A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 9
  front || stop "QA app not back in front after its restart"
  snap 1_restart
  # 2. rescan: the fixture library is read again
  A --ez voltcap_test_fixture_roms true; sleep 8
  snap 2_rescan
  # 3. layouts previewed one after another, then the own one again (a preview writes nothing)
  for l in GRID NIGHT DISCS CLASSIC GREENCORE TILES; do A --ez voltcap_test_preview true --es voltcap_test_layout $l; sleep 2.5; done
  A --ez voltcap_test_preview false; sleep 3
  snap 3_layouts
  # 4. Edit Home opened and discarded (nothing saved)
  A --es voltcap_test_edithome open; sleep 2; A --es voltcap_test_edithome discard; sleep 2
  snap 4_edithome
  # 5. tuner on simulated hardware: a game's profile, the heat guard hot then cool, Recents back to a library game, end
  A --es voltcap_test_tunesim on; sleep 2
  A --es voltcap_test_tunesim "launch:com.qa.fixture.game|QA game|com.qa.fixture.game"; sleep 2
  A --es voltcap_test_tunesim temp:96; sleep 20; A --es voltcap_test_tunesim temp:60; sleep 3
  A --es voltcap_test_tunesim end; sleep 2; A --es voltcap_test_tunesim off; sleep 2
  snap 5_tuner
  # 6. a second restart
  am force-stop $P; sleep 2; A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 9
  front || stop "QA app not back in front after the second restart"
  snap 6_restart2
  log "fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')"
  echo DONE >> $L ;;
esac
