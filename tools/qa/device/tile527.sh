#!/system/bin/sh
# Sprint 527 - Tile home rows (TileRows order) and the background clip's light, isolated QA app ONLY (com.shelf.launcher.qa).
# Never wakes / unlocks; keys only while the QA app is in front (else STOP). The owner's QA snapshot files are copied aside
# first and put back byte-for-byte at the end (the snapshot hook overwrites them).
#   sh tile527.sh rows TAG      orders x QaClip dump + screenshot; a saved order across a restart, a category switch, a layout switch
#   sh tile527.sh video TAG     the synthetic clips (white / bright / dark / cuts) behind Tile home (ART background, session only)
#   sh tile527.sh video2 TAG    the same after the clip-light change, settings saved first, plus a recorded start / R2 / L2 / next
#                               game / game window / off flow and the cuts clip
#   sh tile527.sh restore TAG   settings back (snapshot), owner's snapshot files back, compared
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
k() { gate; input keyevent $(echo "$1" | tr '+' '\n' | sed 's/^/KEYCODE_/' | tr '\n' ' '); }
picker() { grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_B; sleep 1.5; log "picker dismissed"; }; }
shot() { sleep ${2:-2.5}; screencap -p $O/$1.png; rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; sleep 1.3; cp $F/qa_clip/${TAG}_$1.json $O/$1.json 2>/dev/null; log "shot $1"; }
case $MODE in
rows)
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept: $(ls $O/snapbak | tr '\n' ' ')"; }
  A --ez voltcap_test_nolaunch true; sleep 3; picker
  A --es voltcap_test_snapshot save; sleep 2
  A --es voltcap_test_fixture "names=real,art=portrait,meta=none"; sleep 1
  A --es voltcap_test_layout TILES --es voltcap_test_category ALL --es voltcap_test_game "Super Mario World"; sleep 4; picker
  A --es voltcap_test_dock MIDDLE; A --es voltcap_test_pref "part_shifts="; A --es voltcap_test_pref "widget_positions="; sleep 1
  n=0; for ORD in "" "buttons,cats,tiles,info" "tiles,info,cats,buttons" "cats,tiles,buttons,info" "info,tiles,cats,buttons"; do
    n=$((n+1)); A --es voltcap_test_pref "tile_row_order=$ORD"; shot rows${n}; log "rows$n order=[$ORD]"; done
  for D in TOP BOTTOM; do A --es voltcap_test_dock $D; A --es voltcap_test_pref "tile_row_order="; shot ${D}_default
    A --es voltcap_test_pref "tile_row_order=cats,tiles,buttons,info"; shot ${D}_reordered; done
  A --es voltcap_test_dock MIDDLE; A --es voltcap_test_pref "tile_row_order=buttons,tiles,cats,info"; shot saved_before_restart
  am force-stop $P; sleep 1; A; sleep 5; picker; shot after_restart
  gate; k BUTTON_R2; shot after_category_switch
  A --es voltcap_test_layout CLASSIC; sleep 3; A --es voltcap_test_layout TILES; shot after_layout_switch
  log "order now: $(grep -o 'tile_row_order[^,]*' /data/data/$P/shared_prefs/voltcap_features.xml 2>/dev/null)" ;;
video)
  mkdir -p $F/qa_video; cp /data/local/tmp/v_*.mp4 $F/qa_video/ 2>/dev/null; ls $F/qa_video >> $L
  A --es voltcap_test_pref "tile_row_order="; A --es voltcap_test_pref "tile_switch_plain=false"; A --es voltcap_test_bg ART; A --es voltcap_test_video off; sleep 3
  shot vid_none 3
  for v in white bright dark cuts; do A --es voltcap_test_video file://$F/qa_video/v_$v.mp4; shot vid_$v 5; done
  A --es voltcap_test_video file://$F/qa_video/v_cuts.mp4; sleep 2; . /data/local/tmp/rec.sh; REC_SEG=6; rec_start $O/vid_cuts_rec 8; sleep 7.5; rec_stop
  A --es voltcap_test_video off; shot vid_off 3 ;;
video2)
  # After the clip-light change: the same clips, then one recording through start, category switch, next game, the game
  # window open / closed, clip off / on (the light must glide, never flash). Settings saved first (restore puts them back).
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept: $(ls $O/snapbak | tr '\n' ' ')"; }
  A --ez voltcap_test_nolaunch true; sleep 3; picker
  A --es voltcap_test_snapshot save; sleep 2
  A --es voltcap_test_fixture "names=real,art=portrait,meta=none"; sleep 1
  A --es voltcap_test_layout TILES --es voltcap_test_category ALL --es voltcap_test_game "Super Mario World"; sleep 4; picker
  A --es voltcap_test_dock MIDDLE; A --es voltcap_test_pref "tile_row_order="
  mkdir -p $F/qa_video; cp /data/local/tmp/v_*.mp4 $F/qa_video/ 2>/dev/null; ls $F/qa_video >> $L
  A --es voltcap_test_pref "tile_switch_plain=false"; A --es voltcap_test_bg ART; A --es voltcap_test_video off; sleep 3
  shot vid_none 3
  for v in white bright dark cuts; do A --es voltcap_test_video file://$F/qa_video/v_$v.mp4; shot vid_$v 5; done
  A --es voltcap_test_video off; sleep 2; . /data/local/tmp/rec.sh; REC_SEG=8; rec_start $O/vid_flow_rec 16
  sleep 1; log "rec: clip on (white)"; A --es voltcap_test_video file://$F/qa_video/v_white.mp4; sleep 3.5
  log "rec: R2"; k BUTTON_R2; sleep 2; log "rec: L2"; k BUTTON_L2; sleep 1.5
  log "rec: right"; k DPAD_RIGHT; sleep 1.5
  log "rec: A (game window)"; k BUTTON_A; sleep 2; log "rec: B"; k BUTTON_B; sleep 2
  log "rec: clip off"; A --es voltcap_test_video off; sleep 2; rec_stop
  A --es voltcap_test_video file://$F/qa_video/v_cuts.mp4; sleep 2; REC_SEG=8; rec_start $O/vid_cuts_rec 9; sleep 8.5; rec_stop
  A --es voltcap_test_video off; shot vid_off 3 ;;
restore)
  A --es voltcap_test_video off --es voltcap_test_bg off; sleep 1
  A --es voltcap_test_snapshot restore; sleep 3; log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  rm -rf $F/qa_video; log "fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" ;;
esac
