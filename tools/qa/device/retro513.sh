#!/system/bin/sh
# Update 513: Retroid Pocket Classic + Nova smoke pass — isolated Voltcap QA app ONLY (com.shelf.launcher.qa: launches
# blocked, no sync). Run per device with adb -s <serial>, one device at a time is fine, never more than two connected.
#   sh retro513.sh TAG [sections]    sections: id lay modes rapid vault idle (default all)
# id    read-only: model, Android, display size / density / refresh rates / rotation, USB vs wireless is checked on the PC.
# lay   all 53 home layouts (preview only): home still + QaClip dump, A opens the game window (still + dump), B closes,
#       focus back on the home window; long-title fixture names. Results: <TAG>/log.txt + pictures (pulled for geomcheck / popclip).
# modes Motion Off and popup Instant / Follow layout with Game info "Just appear" (first frames recorded, 2 s each).
# rapid A B A B three times on 4 layouts: one window at most, closes, no launch.
# vault Save Vault (memory-card sheet), generated cartridge and CD at 0/89/90/91/180 degrees.
# idle  CPU % and frames over 6 s, 15 s after the last key, on 4 layouts.
# The QA app's settings are snapshotted first and restored + verified at the end. Nothing of the real Voltcap is touched.
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; shift; S=${*:-id lay modes rapid vault idle}
O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" | tail -1; }
# Never press a key unless the QA app has the focus (513: the Nova's real Voltcap showed its own update prompt; a key there
# could answer it). Anything else in front stops the whole run.
k() { f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) ;; *) log "ABORT: focus is not the QA app: $f"; echo DONE >> $L; exit 2;; esac
  input keyevent $1; sleep ${2:-0.7}; }
shot() { screencap -p $O/$1.png; }
clip() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; sleep 1.2; cp $F/qa_clip/${TAG}_$1.json $O/ 2>/dev/null; }
log() { echo "$*" >> $L; }
picker() { grep -q "^PICKER $(pidof $P)" $F/voltcap_qa_screen 2>/dev/null && { k 97 1.5; log "picker dismissed"; }; }
LAYS="CLASSIC TILES HERO GRID TV DASH LIVE SIDE CROSS SPOT BAR SHELF HUB RESUME CHAN CIRCUIT LIBRARY STACKS TIMELINE ARCADE CONSOLES DISCS HERO_TILES TWIN_DESK TWIN_ICONS TWIN_SHOWCASE CRATE CARDS CUSTOM POCKET_CLASSIC POCKET_RIBBON GREENCORE POCKET CUBE MEMCARD CASEFLOW BOX STORAGE FILESELECT ORBIT AIRY NIGHT BLADES BUBBLES CAMERA PANELS MAGAZINE SHOP TERMINAL CINEMA ARCTIC COVERDETAILS ARTLIB"
log "start $(date +%T) sections=$S"
logcat -c
A --ez voltcap_test_nolaunch true; sleep 4; picker
# (input keyevent 59 in idle goes through k as well)
A --es voltcap_test_snapshot save; sleep 2; k 97 1
A --ez voltcap_test_preview true; sleep 1
for s in $S; do case $s in
id) log "model=$(getprop ro.product.model) device=$(getprop ro.product.device) android=$(getprop ro.build.version.release) sdk=$(getprop ro.build.version.sdk)"
  log "size=$(wm size | tr '\n' ' ') density=$(wm density | tr '\n' ' ')"
  log "rotation=$(dumpsys input | grep -m1 -o 'SurfaceOrientation: [0-9]') displays=$(dumpsys display | grep -c 'mDisplayId=')"
  dumpsys display | grep -oE 'DisplayMode\{id=[0-9]+, width=[0-9]+, height=[0-9]+[^}]*refreshRate=[0-9.]+' | sort -u >> $L
  log "qa=$(dumpsys package $P | grep -m1 versionName) real=$(dumpsys package com.shelf.launcher | grep -m1 versionName) realPid=$(pidof com.shelf.launcher)" ;;
lay) A --es voltcap_test_fixture "names=long,art=wide,meta=none"; sleep 2
  n=0; for LY in $LAYS; do
    A --es voltcap_test_layout $LY; sleep 3.5; picker; H=$(W); shot lay_${LY}_0; clip lay_${LY}_0
    k 96 1.8; W1=$(W); [ "$W1" = "$H" ] && { k 96 1.8; W1=$(W); log "$LY A again"; }
    shot lay_${LY}_1; clip lay_${LY}_1
    k 97 1.4; W2=$(W); [ "$W2" != "$H" ] && { k 97 1.2; W2=$(W); log "$LY B again"; }
    log "lay $LY opened=$([ "$W1" != "$H" ] && echo yes || echo NO) closed=$([ "$W2" = "$H" ] && echo yes || echo NO)"; n=$((n+1))
  done; log "lay done $n layouts"; A --es voltcap_test_fixture off; sleep 1 ;;
modes) A --es voltcap_test_pref "anim_info=NONE"; sleep 0.8
  A --es voltcap_test_layout SHELF --es voltcap_test_game "Mario Kart 64"; sleep 3
  for M in OFF NORMAL; do for PA in INSTANT LAYOUT; do
    A --es voltcap_test_pref "motion_level=$M"; sleep 0.6; A --es voltcap_test_pref "anim_popup=$PA"; sleep 1.2; H=$(W)
    screenrecord --time-limit 2 $O/m_${M}_${PA}.mp4 & sleep 0.6; k 96 1.8; wait; W1=$(W); k 97 1.5; W2=$(W)
    log "modes $M/$PA opened=$([ "$W1" != "$H" ] && echo yes || echo NO) closedByB=$([ "$W2" = "$H" ] && echo yes || echo NO)"
  done; done
  A --es voltcap_test_pref "motion_level=NORMAL"; sleep 0.5; A --es voltcap_test_pref "anim_popup=LAYOUT"; sleep 0.5 ;;
rapid) for LY in SHELF TILES STACKS COVERDETAILS; do
    A --es voltcap_test_layout $LY --es voltcap_test_game "Mario Kart 64"; sleep 3.5; picker; H=$(W)
    r=""; for i in 1 2 3; do k 96 0.12; k 97 0.12; k 96 0.12; k 97 2.0; [ "$(W)" = "$H" ] && r="${r}C" || { r="${r}O"; k 97 1.2; }; done
    shot rapid_$LY; log "rapid $LY $r"
  done ;;
vault) A --es voltcap_test_cards "0,40,89,90,140,180"; sleep 3; shot vault_cards; A --es voltcap_test_cards off; sleep 1
  for y in 0 89 90 91 180; do k 97 0.8; A --es voltcap_test_inspect "Pokemon_-_Emerald_Version||$y|0"; sleep 3; shot cart_y$y; done
  for y in 0 90 180; do k 97 0.8; A --es voltcap_test_inspect "Crash_Bandicoot||$y|0"; sleep 3; shot cd_y$y; done; k 97 0.8 ;;
idle) for LY in SHELF TILES STACKS CHAN; do
    A --es voltcap_test_layout $LY; sleep 3; picker; k 59 15
    dumpsys gfxinfo $P reset >/dev/null
    C=$(top -b -n 2 -d 6 -p $(pidof $P) | grep " $P" | tail -1 | awk '{print $9}')
    log "idle $LY cpu=${C}% frames6s=$(dumpsys gfxinfo $P | grep -m1 'Total frames rendered' | awk '{print $4}')"
  done ;;
esac; done
A --ez voltcap_test_preview false; sleep 0.5
k 97 0.8; A --es voltcap_test_snapshot restore; sleep 3
QP=$(pidof $P)
log "restore: $(cat $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
log "launchesBlocked=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked') fatal=$(logcat -d | grep -c 'FATAL EXCEPTION')"
log "aynCalls(qa pid $QP)=$(logcat -d | grep -iE 'PServer|VendorAdapter|RootShell' | grep -c " $QP ")"
log "end $(date +%T)"; echo DONE >> $L
