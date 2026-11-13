#!/system/bin/sh
# Sprint 528 visual QA (isolated QA app ONLY, com.shelf.launcher.qa): never wakes / unlocks the handheld; a key only while the QA
# app is in front (else STOP). Real-cover fixture: pictures QA pushed into the QA app's OWN files/qa_covers (art=covers, matched
# by game name; games without one show the fallback). Owner settings saved first (snapshot) and put back by `restore`.
#   sh sp528.sh badge TAG     Tile home: profile pictures vs the first pill - docks Top / Middle / Bottom, x1.0 / x1.3, pill row
#                             scrolled to the end (long names), two themes; QaClip dumps beside the shots
#   sh sp528.sh covers TAG    real covers: Tile home, Classic row, game window, category looks (Tiles, Wall, Cinema, Grid, Slides,
#                             Drawers), x1.0
#   sh sp528.sh music TAG     Edit Home: the music player's stand-in with a row / the buttons moved to the top; saved; reset layout
#   sh sp528.sh restore TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
k() { gate; input keyevent $(echo "$1" | tr '+' '\n' | sed 's/^/KEYCODE_/' | tr '\n' ' '); }
picker() { grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_B; sleep 1.5; log "picker dismissed"; }; }
shot() { sleep ${2:-1.5}; gate; screencap -p $O/$1.png; A --es voltcap_test_clip "$1"; sleep 1.3; cp "$F/qa_clip/$1.json" $O/ 2>/dev/null; log "shot $1"; }
setup() {
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  A --ez voltcap_test_nolaunch true; sleep 3; picker
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_fixture "names=real,art=${ART:-covers},meta=none"; sleep 2
  A --es voltcap_test_layout TILES --es voltcap_test_category ALL --es voltcap_test_game "Super Mario World"; sleep 3; picker
}
case $MODE in
badge)
  setup; A --es voltcap_test_pref "tile_switch=true"; A --es voltcap_test_pref "tile_switch_profiles=true"; A --es voltcap_test_pref "tile_row_order="; sleep 1
  for SC in off font=1.3; do A --es voltcap_test_scale "$SC"; sleep 1.5; A --es voltcap_test_layout TILES; sleep 2; picker
    for D in TOP MIDDLE BOTTOM; do A --es voltcap_test_dock $D; A --es voltcap_test_category ALL; shot b_${SC}_${D}_start 2
      for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18; do k BUTTON_R2; sleep 0.35; done; shot b_${SC}_${D}_end 1.5; done
  done
  A --es voltcap_test_scale off; A --es voltcap_test_dock MIDDLE; A --es voltcap_test_category ALL; sleep 1
  for T in MONO FROST_TYPE; do A --es voltcap_test_theme $T; sleep 2.5; shot b_theme_$T 1; done ;;
covers)
  setup; A --es voltcap_test_pref "tile_row_order="; sleep 1
  for G in "Super Mario World" "Shadow of the Colossus" "Mario Kart DS" "Daxter"; do A --es voltcap_test_game "$G"; shot c_tiles_$(echo $G | tr -cd 'A-Za-z') 2; done
  A --es voltcap_test_layout CLASSIC; sleep 2.5; picker; A --es voltcap_test_game "Crash Bandicoot"; shot c_classic 2
  A --ez voltcap_test_window true; shot c_window 2.5; k BUTTON_B; sleep 1.2
  A --es voltcap_test_layout TILES; sleep 2; picker; A --es voltcap_test_pref "pb_follow_home=false"; sleep 0.5
  for V in TILES WALL CINEMA GRID SLIDES DRAWERS; do A --es voltcap_test_pref "pb_view=$V"; sleep 0.6
    A --es voltcap_test_category N64; sleep 1; A --es voltcap_test_catscreen open; shot c_look_$V 2.5
    A --es voltcap_test_catscreen close; sleep 1.2; done ;;
music)
  ART=portrait; setup; A --es voltcap_test_pref "tile_row_order="; A --es voltcap_test_pref "widget_positions="; sleep 1
  E() { A --es voltcap_test_edithome "$1"; sleep 1.2; }
  E open; shot m_open; E select:part:TILES:row; k DPAD_UP; sleep 0.8; k DPAD_UP; shot m_row_top; E discard
  E open; E select:shortcuts; k DPAD_UP; sleep 0.8; k DPAD_UP; shot m_buttons_top; E discard
  E open; E select:part:TILES:row; k DPAD_UP; sleep 0.8; k DPAD_UP; E save; shot m_saved_home 2.5
  E open; E resetlayout:TILES; E save; shot m_reset 2 ;;
restore)
  A --es voltcap_test_catscreen close --es voltcap_test_scale off --es voltcap_test_fixture off; sleep 1
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  log "fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" ;;
esac
