#!/system/bin/sh
# Update 529 hotfix (blur): identical-frame captures of every blurred surface - isolated QA app ONLY (com.shelf.launcher.qa).
# Never wakes / unlocks; a key only while the QA app is in front. Owner settings: snapshot saved first, restored by `restore`.
#   sh blur529.sh shots TAG     blurred hero (bright / dark art), popup over it, Night menu, Moon blade scene (half / full res),
#                               full-screen theme blur, category screen, Arctic / Art library / Cover details art blur
#   sh blur529.sh restore TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
k() { gate; input keyevent KEYCODE_$1; }
picker() { grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_B; sleep 1.5; log "picker dismissed"; }; }
shot() { sleep ${2:-2.5}; gate; screencap -p $O/$1.png; log "shot $1"; }
LOOK() { A --es voltcap_test_pref "theme_looks={\"MOON_BLADE\":{\"blur\":$1,\"ba\":\"FULL\",\"dim\":0}}"; sleep 1.2; }
case $MODE in
shots)
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; picker
  A --es voltcap_test_snapshot save; sleep 2
  A --es voltcap_test_artfix "delay=0"; sleep 2
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_theme MOON_BLADE; sleep 3; picker; LOOK 0
  A --es voltcap_test_bg BLURRED --es voltcap_test_layout CLASSIC; sleep 3; picker
  A --es voltcap_test_game "Super Mario World"; shot b1_hero_bright 7
  A --es voltcap_test_game "Shadow of the Colossus"; shot b2_hero_dark 7
  A --ez voltcap_test_window true; shot b3_popup 3; k BUTTON_B; sleep 1.5
  A --es voltcap_test_layout NIGHT; sleep 3; picker; shot b4_night 3
  LOOK 70; shot b4b_night_fullblur 3; LOOK 0
  A --es voltcap_test_layout TILES; sleep 3; picker; shot b5_tiles 3
  A --es voltcap_test_bg ANIMATED --es voltcap_test_layout CLASSIC; sleep 3; picker
  A --es voltcap_test_pref "scene_half_res=true"; shot b6_moon_halfres 3
  A --es voltcap_test_pref "scene_half_res=false"; shot b7_moon_fullres 3
  LOOK 70; shot b8_moon_fullblur 3
  # frame timing of the animated scene under the full-screen blur (8 s, nothing pressed)
  dumpsys gfxinfo $P reset >/dev/null 2>&1; sleep 8
  dumpsys gfxinfo $P | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" > $O/perf_fullblur.txt
  LOOK 0; sleep 1.5; dumpsys gfxinfo $P reset >/dev/null 2>&1; sleep 8
  dumpsys gfxinfo $P | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" > $O/perf_noblur.txt
  LOOK 70; sleep 1.5
  A --es voltcap_test_bg BLURRED; sleep 2; shot b9_hero_plus_fullblur 3
  LOOK 0; A --es voltcap_test_pref "scene_half_res=true"
  A --es voltcap_test_category N64; sleep 1; A --es voltcap_test_catscreen open; shot b10_catscreen 3
  A --es voltcap_test_catscreen close; sleep 1.5
  for LY in ARCTIC ARTLIB COVERDETAILS; do A --es voltcap_test_layout $LY --es voltcap_test_category ALL; sleep 3; picker
    A --es voltcap_test_game "Super Mario World"; shot b11_$LY 3; done
  log "shots done" ;;
restore)
  A --es voltcap_test_catscreen close --es voltcap_test_scale off --es voltcap_test_fixture off --es voltcap_test_artfix off --es voltcap_test_bg off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  log "fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" ;;
esac
