#!/system/bin/sh
# Update 529, achievements compact hotfix: the per-game achievement list, before / after - isolated QA app ONLY
# (com.shelf.launcher.qa), fixture library with synthetic sets (QaArtFixture; the new build: sets of 0 / 1 / 10 / 120 and synthetic
# comments). Never wakes / unlocks: a key only while the QA app is in front, awake and unlocked. Owner settings: snapshot saved
# first, restored by `restore`.
#   sh ach529.sh run TAG ["GAME" ...]   per game: the window, More, Achievements; the list as it opens; resting on a row 2 s; A on
#                                        it; its Comments; B, B; the control bar's first panel; B, B. Then the same list at x1.3, and
#                                        a recording of fast moves + open / close. Without games: one per set size from the fixture log.
#   sh ach529.sh restore TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys window | grep mCurrentFocus | tail -1 | grep -q "$P" || stop "QA app not in front"; }
k() { gate; input keyevent KEYCODE_$1; sleep ${2:-0.7}; }
picker() { grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_B 1.5; log "picker dismissed"; }; }
shot() { sleep ${2:-0.6}; gate; screencap -p $O/$1.png; log "shot $1"; }
. /data/local/tmp/rec.sh 2>/dev/null
openPage() { # the game window's own Achievements page (window, More, Achievements)
  A --es voltcap_test_game "$1"; sleep 2; A --ez voltcap_test_window true; sleep 2.2
  A --es voltcap_test_winmore on; sleep 1.4; shot $2_win 0.2
  k DPAD_RIGHT 0.6; k DPAD_CENTER 2.5
}
openList() { # the per-game achievement list (as the window's Full list opens it)
  A --es voltcap_test_game "$1"; sleep 2; A --ez voltcap_test_achlist true; sleep 2.5
}
page() { # GAME NAME
  openPage "$1" "p$2"; shot p$2_1open 0.3; k DPAD_DOWN 0.4; k DPAD_DOWN 2.2; shot p$2_2rest 0.1
  k DPAD_CENTER 1.0; shot p$2_3rowA 0.1; k DPAD_DOWN 0.6; shot p$2_3b 0.1; k DPAD_CENTER 1.4; shot p$2_4comments 0.1
  k BUTTON_B 0.8; shot p$2_5b1 0.1; k BUTTON_B 0.8; k BUTTON_B 0.8; k BUTTON_B 1.2; shot p$2_9closed 0.1
}
one() { # GAME NAME
  log "game $1 ($2)"
  openList "$1" "$2"; shot $2_1open 0.3
  k DPAD_DOWN 0.4; k DPAD_DOWN 2.2; shot $2_2rest 0.1
  k DPAD_CENTER 1.0; shot $2_3rowA 0.1
  k DPAD_DOWN 0.5; shot $2_3b 0.1; k DPAD_DOWN 0.5; shot $2_3c 0.1; k DPAD_CENTER 1.4; shot $2_4comments 0.1
  k BUTTON_B 0.8; shot $2_5b1 0.1; k BUTTON_B 0.8; shot $2_6b2 0.1
  for i in 1 2 3 4 5 6; do k DPAD_UP 0.25; done; k DPAD_CENTER 0.9; shot $2_7panel 0.1
  k BUTTON_B 0.8; shot $2_8b3 0.1; k BUTTON_B 1.2; shot $2_9closed 0.1
  dumpsys window | grep mCurrentFocus | tail -1 >> $L
}
case $MODE in
run)
  dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  logcat -c
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate; picker
  A --es voltcap_test_snapshot save; sleep 2
  A --es voltcap_test_artfix "delay=0"; sleep 2
  A --ez voltcap_test_preview true --es voltcap_test_perf FULL --es voltcap_test_category ALL --es voltcap_test_layout CLASSIC; sleep 3; picker
  shift 2
  if [ $# -eq 0 ]; then
    for n in 1 10 120 0; do
      g=$(logcat -d -s VoltcapQA | grep "achfix n=$n game=" | head -1 | sed 's/.*game=//')
      [ -n "$g" ] && echo "$n|$g" >> $O/games.txt
    done
  else for g in "$@"; do echo "x|$g" >> $O/games.txt; done; fi
  log "games: $(tr '\n' ';' < $O/games.txt)"
  while IFS='|' read n g; do [ "$n" = 0 ] || one "$g" "n$n"; done < $O/games.txt
  # the window's own page: a set of 10 and no set at all
  while IFS='|' read n g; do case $n in 10|0) page "$g" "$n";; esac; done < $O/games.txt
  # x1.3 on the first game with a set of 10 (or the first one)
  g=$(grep -m1 "^10|" $O/games.txt | cut -d'|' -f2); [ -z "$g" ] && g=$(head -1 $O/games.txt | cut -d'|' -f2)
  A --es voltcap_test_scale "font=1.3"; sleep 2; openList "$g" s13; shot s13_1open 0.3; k DPAD_CENTER 1.0; shot s13_3rowA 0.1
  for i in 1 2 3 4 5 6; do k DPAD_UP 0.25; done; k DPAD_CENTER 0.9; shot s13_7panel 0.1
  k BUTTON_B 0.8; k BUTTON_B 0.8; k BUTTON_B 1.2; A --es voltcap_test_scale off; sleep 1.5
  # fast moves, open / close, recorded
  openList "$g" rec; REC_SEG=9 rec_start $O/v_fast 9; sleep 0.6
  for i in 1 2 3 4 5 6 7 8; do gate; input keyevent KEYCODE_DPAD_DOWN; sleep 0.09; done; sleep 0.8
  k DPAD_CENTER 0.9; k DPAD_CENTER 0.9; k DPAD_CENTER 0.9; k BUTTON_B 0.9
  k DPAD_UP 0.3; k DPAD_UP 0.3; k DPAD_CENTER 1.0; k BUTTON_B 1.0
  rec_stop; sleep 0.5; k BUTTON_B 1.2
  log "run done fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" ;;
restore)
  A --es voltcap_test_scale off --es voltcap_test_fixture off --es voltcap_test_artfix off --es voltcap_test_perf AUTO; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  am force-stop $P; log "QA app stopped" ;;
esac
