#!/system/bin/sh
# Update 529 blur hotfix: the blurred hero / baked-blur paths with real decoded pictures - isolated QA app ONLY. QA fixture art
# (art=wide: generated 16:9 pictures with lettering and hard edges, used as hero too; art=lowres: 64 x 96 shown big; art=portrait).
# Never wakes / unlocks; keys only while the QA app is in front. Owner snapshot kept and restored.
#   sh blur529h.sh shots TAG | restore TAG
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
BG() { A --es voltcap_test_bg BLURRED; sleep 2; }
perf() { dumpsys gfxinfo $P | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" > $O/perf_$1.txt; }
case $MODE in
shots)
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; picker
  A --es voltcap_test_snapshot save; sleep 2
  A --es voltcap_test_artfix "delay=0"; sleep 2
  A --es voltcap_test_fixture "names=real,art=wide,meta=real"; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_theme MOON_BLADE; sleep 3; picker; LOOK 0
  # (the background after the layout, in its own call: the layout hook previews the saved look and would undo it)
  A --es voltcap_test_layout CLASSIC --es voltcap_test_category ALL; sleep 3; picker; BG
  A --es voltcap_test_game "Super Mario World"; shot h1_wide_a 6
  A --es voltcap_test_game "Shadow of the Colossus"; shot h2_wide_b 6
  A --ez voltcap_test_window true; shot h3_popup 3; k BUTTON_B; sleep 1.5
  A --es voltcap_test_category N64; sleep 0.25; gate; screencap -p $O/h4_cat_mid.png; log "shot h4_cat_mid"; shot h4b_cat_settled 3
  A --es voltcap_test_category ALL; sleep 2
  # scrolling through games: every step decodes and blurs a new hero
  A --es voltcap_test_game "Super Mario World"; sleep 4; dumpsys gfxinfo $P reset >/dev/null 2>&1
  for i in 1 2 3 4 5 6 7 8; do k DPAD_RIGHT; sleep 0.7; done; sleep 1.5; perf scroll
  dumpsys gfxinfo $P reset >/dev/null 2>&1; sleep 6; perf idle
  A --es voltcap_test_fixture "names=real,art=lowres,meta=real"; sleep 2; A --es voltcap_test_game "Super Mario World"; shot h5_lowres 6
  A --es voltcap_test_fixture "names=real,art=portrait,meta=real"; sleep 2; A --es voltcap_test_game "Super Mario World"; shot h6_portrait 6
  A --es voltcap_test_fixture "names=real,art=wide,meta=real"; sleep 2
  A --es voltcap_test_layout DISCS; sleep 3; picker; BG; A --es voltcap_test_game "Super Mario World"; shot h7_discs 6
  A --es voltcap_test_layout TILES; sleep 3; picker; BG; A --es voltcap_test_game "Super Mario World"; shot h9_tiles 6
  A --es voltcap_test_layout CLASSIC; sleep 3; picker; BG; A --es voltcap_test_game "Super Mario World"; LOOK 70; shot h8_hero_fullblur 5; LOOK 0
  log "shots done" ;;
restore)
  A --es voltcap_test_catscreen close --es voltcap_test_scale off --es voltcap_test_fixture off --es voltcap_test_artfix off --es voltcap_test_bg off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  log "fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" ;;
esac
