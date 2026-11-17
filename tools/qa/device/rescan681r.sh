#!/system/bin/sh
# 681r (owner 17 Nov: "the only game of a console folder, deleted, still listed after Rescan library; the category stays"):
# end to end in the isolated QA app ONLY (com.shelf.launcher.qa) on SYNTHETIC stand-in files in its OWN folder
# (<its external files>/roms - nothing of the owner's). Each step rescans the way Rescan library does (an explicit scan of the
# folder) and reads the library as the screen has it (voltcap_test_libdump -> voltcap_qa_lib.txt).
#   sh rescan681r.sh run TAG      the cases below; PASS / FAIL per case in log.txt, one line each
#   sh rescan681r.sh restore TAG  removes the stand-ins it made, rescans, puts the QA app's snapshot back
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; R=$F/roms; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; echo DONE >> $L; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
rescan() { A --ez voltcap_test_fixture_roms true; sleep 6; gate; }
dump() { rm -f $F/voltcap_qa_lib.txt; A --ez voltcap_test_libdump true; i=0; while [ ! -s $F/voltcap_qa_lib.txt ] && [ $i -lt 30 ]; do sleep 0.3; i=$((i+1)); done
  cp $F/voltcap_qa_lib.txt $O/$1.lib.txt 2>/dev/null; screencap -p $O/$1.png; }
has() { grep -qF "$2" $O/$1.lib.txt 2>/dev/null; }
check() { if eval "$2"; then log "PASS $1"; else log "FAIL $1"; fi; }
inplat() { grep '^platforms=' $O/$1.lib.txt | cut -d= -f2 | tr ',' '\n' | grep -qxF "$2"; }
MARK="QA681r"
case $MODE in
run)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 6; gate
  grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; }
  A --es voltcap_test_snapshot save; sleep 2
  dump base
  # a console the fixture has no games of
  C=""; for c in xbox360 3do jaguar vectrex; do grep -q "^game=.*/roms/$c/" $O/base.lib.txt || { C=$c; break; }; done
  [ -n "$C" ] || stop "no free console folder"
  log "console folder: $C"
  # 1. the only game of a console
  mkdir -p $R/$C; echo x > "$R/$C/$MARK Only Game.iso"; rescan; dump c1_added
  PL=$(grep "^game=.*$MARK Only Game" $O/c1_added.lib.txt | cut -d= -f2 | cut -d'|' -f1)
  check "c1 the stand-in is listed (console '$PL')" "[ -n \"$PL\" ]"
  A --es voltcap_test_category "$PL"; sleep 2; dump c1_selected
  check "c1 its category is selected" "has c1_selected 'filter=$PL '"
  rm "$R/$C/$MARK Only Game.iso"; rmdir $R/$C 2>/dev/null; rescan; dump c2_deleted
  check "c2 deleted only game: not listed" "! has c2_deleted '$MARK Only Game'"
  check "c2 its console is no category any more" "! inplat c2_deleted '$PL'"
  check "c2 the selection fell back to All" "has c2_deleted 'filter=ALL '"
  rescan; dump c3_twice
  check "c3 rescanned twice: still gone" "! has c3_twice '$MARK Only Game'"
  N0=$(grep -c '^game=' $O/c1_selected.lib.txt); N3=$(grep -c '^game=' $O/c3_twice.lib.txt)
  check "c3 nothing else lost ($N0 -> $N3)" "[ $((N0 - 1)) = $N3 ]"
  am force-stop $P; sleep 1; A; sleep 7; gate; dump c4_restart
  check "c4 after a restart: still gone" "! has c4_restart '$MARK Only Game'"
  check "c4 after a restart: the others are there" "[ $(grep -c '^game=' $O/c4_restart.lib.txt) = $N3 ]"
  # 5. one of several in a folder
  mkdir -p $R/$C; echo a > "$R/$C/$MARK Keep.iso"; echo b > "$R/$C/$MARK Drop.iso"; rescan
  rm "$R/$C/$MARK Drop.iso"; rescan; dump c5_one_of_two
  check "c5 one of two deleted: it is gone" "! has c5_one_of_two '$MARK Drop'"
  check "c5 one of two deleted: the other stays" "has c5_one_of_two '$MARK Keep'"
  # 6. a whole game folder (a PS3 folder game)
  mkdir -p "$R/ps3/$MARK Folder Game/PS3_GAME"; echo d > "$R/ps3/$MARK Folder Game/PS3_DISC.SFB"; rescan; dump c6_folder_added
  check "c6 folder game listed" "has c6_folder_added '$MARK Folder Game'"
  rm -r "$R/ps3/$MARK Folder Game"; rescan; dump c6_folder_deleted
  check "c6 deleted game folder: gone" "! has c6_folder_deleted '$MARK Folder Game'"
  # 7. put back: the same file is the same game (its path - every saved thing is keyed by it)
  K1=$(grep "$MARK Keep" $O/c5_one_of_two.lib.txt | cut -d'|' -f3)
  rm "$R/$C/$MARK Keep.iso"; rescan; echo a > "$R/$C/$MARK Keep.iso"; rescan; dump c7_readded
  K2=$(grep "$MARK Keep" $O/c7_readded.lib.txt | cut -d'|' -f3)
  check "c7 re-added: same identity ($K2)" "[ -n \"$K1\" ] && [ \"$K1\" = \"$K2\" ]"
  log "run done fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')"; echo DONE >> $L ;;
restore)
  for c in xbox360 3do jaguar vectrex; do rm -f $R/$c/$MARK*; rmdir $R/$c 2>/dev/null; done; rm -rf "$R/ps3/$MARK Folder Game"
  A --ez voltcap_test_fixture_roms true; sleep 5
  A --es voltcap_test_snapshot restore; sleep 3
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/ 2>/dev/null; fi
  am force-stop $P; log "restored; QA app stopped" ;;
esac
