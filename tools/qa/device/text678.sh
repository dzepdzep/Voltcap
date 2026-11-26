#!/system/bin/sh
. "$(dirname "$0")/qaguard.sh" 2>/dev/null || . /data/local/tmp/vqa/qaguard.sh 2>/dev/null || { echo "qaguard.sh missing - push it beside this script" >&2; exit 3; } # (682ar, D390: a real end)
# 678o (live KONKR session): rule-11 text pages on hardware in one pass - HW-14 Settings (search "theme", the Theme,
# Achievements and Categories pages), HW-13 Save Vault (fixture: display only, no save read / written, nothing synced),
# HW-16 category screen (a long console name), HW-20 a game's Achievements list (fixture: nothing new, then 2 marked new by
# the voltcap_test_achnew hook). Isolated QA app ONLY
# (com.shelf.launcher.qa): pages are opened by test hooks (no Back / Home key ever), a screenshot + QaClip dump each, and D-pad
# steps (▼ / ▶) to see rows keep their heights. Never wakes / unlocks; keys only while the QA app has the focus (else it stops
# the QA app and ends, nothing pressed). The QA app's own settings: snapshot saved first, restored and verified at the end.
#   sh text678.sh TAG ["off font=1.3,density=1.15"]   -> /sdcard/pc/TAG/{log.txt,manifest.txt,<SC>_<PAGE>.png,<SC>_<PAGE>_home.json}
# Grade on the host: python3 tools/qa/tools/homecut.py <run dir> (every page dump is named *_home.json for it).
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; SCALES=${2:-off}
OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT; L=$OUT/log.txt; rm -f $F/qa_clip/${TAG}_*
log() { echo "$(date +%T) $*" >> $L; }
. "$(dirname "$0")/evidence.sh" 2>/dev/null || . /data/local/tmp/evidence.sh
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" > /dev/null 2>&1; }
dumpsys power | grep -q "mWakefulness=Awake" || { log "STOP asleep - not woken"; echo DONE >> $L; exit 3; }
dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && { log "STOP locked"; echo DONE >> $L; exit 3; }
pm path $P > /dev/null 2>&1 || { log "STOP: $P not installed - nothing pressed"; echo DONE >> $L; exit 1; }
ev_start $OUT $TAG "scales=$SCALES" "pages=settings,vault,catscreen,achlist" "script_sha256=$(sha256sum "$0" | cut -d' ' -f1)"
echo "target: serial=$(getprop ro.serialno) model=$(getprop ro.product.model) package=$P $(dumpsys package $P | grep -m1 versionName | tr -d ' ')" >> $L
# (D334: the launch-block file first - while it exists the QA app's Back at its root stays in front, and no game starts.)
touch $F/qa_nolaunch $F/voltcap_test_hooks
fin() { A --es voltcap_test_scale off --es voltcap_test_fixture off --es voltcap_test_artfix off --ez voltcap_test_vault_fixture false; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"; log "fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')"
  ev_end $OUT; echo DONE >> $L; }
fg() { f=$(dumpsys window | grep mCurrentFocus | tail -1)
  case "$f" in *com.shelf.launcher.qa*) return 0;; esac
  log "ABORT focus=$f - nothing pressed"; am force-stop $P; ev_end $OUT; echo DONE >> $L; exit 2; }
k() { fg; input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
# (as popclip.sh, update 478: "Who's playing?" comes up again after a restart - closed with B (Keep Player) whenever the app's
# own mark says PICKER, before the first key of a scale; logged.)
nopicker() { for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 1.5; log "profile picker closed with B"; done; }
# shot NAME: a screenshot and the QaClip dump of the same moment (a request file the QA app reads - 523w: world-readable).
shot() { fg; screencap -p $OUT/$1.png; rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; chmod 666 $F/qa_clip_req
  i=0; while [ $i -lt 25 ] && [ ! -s $F/qa_clip/${TAG}_$1.json ]; do sleep 0.3; i=$((i+1)); done
  if [ -s $F/qa_clip/${TAG}_$1.json ]; then cp $F/qa_clip/${TAG}_$1.json $OUT/$1_home.json; log "$1 ok"; else log "$1 NO DUMP"; fi; }
A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 4
A --es voltcap_test_snapshot save; sleep 2
A --es voltcap_test_artfix "delay=0"; sleep 3
A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
for SC in $SCALES; do
  case $SC in off) S=X10;; *) S=X13;; esac
  A --es voltcap_test_scale "$SC"; sleep 2
  A --es voltcap_test_layout CLASSIC --ez voltcap_test_preview true --es voltcap_test_game "Shadow of the Colossus"; sleep 4
  nopicker
  log "== $S ($SC)"
  # HW-14: Settings search (each result's label + section path), then three pages
  A --es voltcap_test_settings Home --es voltcap_test_settings_search theme; sleep 3; shot ${S}_SETSEARCH
  A --es voltcap_test_settings Theme; sleep 3; shot ${S}_SETTHEME; k DPAD_DOWN; k DPAD_DOWN; shot ${S}_SETTHEMED2
  A --es voltcap_test_settings Achievements; sleep 3; shot ${S}_SETACH
  A --es voltcap_test_settings Categories; sleep 3; shot ${S}_SETCAT
  # HW-13: the Save Vault with the fixture saves (display only)
  A --ez voltcap_test_vault_fixture true --es voltcap_test_settings Save_Vault; sleep 5; shot ${S}_VAULT
  k DPAD_DOWN; shot ${S}_VAULTD1; k DPAD_DOWN; shot ${S}_VAULTD2
  A --ez voltcap_test_vault_fixture false --es voltcap_test_layout CLASSIC --ez voltcap_test_preview true; sleep 3
  # HW-16: the category screen with a long console name (this session's labels only)
  A --es voltcap_test_catname "Nintendo Entertainment System Classic Collection" --es voltcap_test_catscreen open; sleep 3; shot ${S}_CATSCREEN
  k DPAD_RIGHT; shot ${S}_CATSCREENR1
  A --es voltcap_test_catscreen close --es voltcap_test_catname off; sleep 2
  A --es voltcap_test_layout CLASSIC --ez voltcap_test_preview true --es voltcap_test_game "Shadow of the Colossus"; sleep 3
  # HW-20: the focused game's Achievements list (fixture achievements, nothing new): opens on its first achievement, whole
  A --ez voltcap_test_achlist true; sleep 3; shot ${S}_ACHLIST
  k DPAD_DOWN; shot ${S}_ACHLISTD1; k DPAD_UP; shot ${S}_ACHLISTU1
  A --es voltcap_test_layout CLASSIC --ez voltcap_test_preview true --es voltcap_test_game "Shadow of the Colossus"; sleep 3
  # HW-20 (678o hook, QA app only): its 2 newest unlocks count as new once more -> the page opens on the new-unlocks stage,
  # ▼ goes on to the controls / the list one press each, ▲ back
  A --ei voltcap_test_achnew 2 --ez voltcap_test_achlist true; sleep 4; shot ${S}_ACHNEW
  k DPAD_DOWN; shot ${S}_ACHNEWD1; k DPAD_DOWN; shot ${S}_ACHNEWD2; k DPAD_UP; shot ${S}_ACHNEWU1
  A --es voltcap_test_layout CLASSIC --ez voltcap_test_preview true; sleep 2
done
fin
