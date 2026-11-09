#!/system/bin/sh
# Update 510 stabilization pass — isolated Voltcap QA app ONLY (com.shelf.launcher.qa: every launch blocked, no sync).
# Screenshots + log into the QA app's own folder. Saves the QA app's settings snapshot first, restores + verifies at the end.
# sh stab510.sh TAG [sections]   sections: pop modes ovl win media (default all)
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; shift; S=${*:-pop modes ovl win media}
O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" | tail -1; }
k() { input keyevent $1; sleep ${2:-0.7}; }
shot() { screencap -p $O/$1.png; }
clip() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; sleep 1.2; cp $F/qa_clip/${TAG}_$1.json $O/ 2>/dev/null; }
log() { echo "$*" >> $L; }
input keyevent KEYCODE_WAKEUP; logcat -c
log "start $(date +%T) qa=$(dumpsys package $P | grep -m1 versionName | tr -d ' ') pid=$(pidof $P)"
A --es voltcap_test_snapshot save; sleep 2; k 97 1
for s in $S; do case $s in
pop) # popup open / B close / rapid A-B, focus token back to the home window each time
  for LY in SHELF STACKS TILES CHAN COVERDETAILS; do
    A --es voltcap_test_layout $LY --es voltcap_test_game "Mario Kart 64"; sleep 3.5; H=$(W)
    k 96 1.6; W1=$(W); shot pop_${LY}_open; clip pop_${LY}
    k 97 1.4; W2=$(W)
    r=""; for i in 1 2 3; do k 96 0.12; k 97 0.12; k 96 0.12; k 97 2.0; [ "$(W)" = "$H" ] && r="${r}C" || { r="${r}O"; k 97 1.2; }; done
    shot pop_${LY}_after
    log "pop $LY opened=$([ "$W1" != "$H" ] && echo yes || echo NO) closedByB=$([ "$W2" = "$H" ] && echo yes || echo NO) rapidAB=$r"
  done ;;
modes) # Motion Off / Subtle / Normal x popup Instant / Follow layout: first frames after A (Game shelf)
  A --es voltcap_test_layout SHELF --es voltcap_test_game "Mario Kart 64"; sleep 3
  for M in OFF SUBTLE NORMAL; do for PA in INSTANT LAYOUT; do
    A --es voltcap_test_pref "motion_level=$M"; sleep 0.6; A --es voltcap_test_pref "anim_popup=$PA"; sleep 1.2; H=$(W)
    screenrecord --time-limit 2 $O/m_${M}_${PA}.mp4 & sleep 0.6; k 96 1.8; wait; W1=$(W); k 97 1.5; W2=$(W)
    log "modes $M/$PA opened=$([ "$W1" != "$H" ] && echo yes || echo NO) closedByB=$([ "$W2" = "$H" ] && echo yes || echo NO)"
  done; done
  A --es voltcap_test_pref "motion_level=NORMAL"; sleep 0.5; A --es voltcap_test_pref "anim_popup=LAYOUT"; sleep 0.5 ;;
ovl) for sc in 1.0 1.3 1.6; do A --es voltcap_test_raoverlay "open=1,scale=$sc,width=420"; sleep 2.5; shot ovl_$sc; done
  A --es voltcap_test_raoverlay "open=1,scale=1.6,width=360"; sleep 2.5; shot ovl_16_narrow
  A --es voltcap_test_raoverlay off; sleep 1; log "ovl done" ;;
win) A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_pref windows_follow_layout=true; sleep 1
  for LY in CHAN ARCTIC STACKS; do A --es voltcap_test_layout $LY; sleep 2.5; A --es voltcap_test_settings Theme; sleep 2.5; shot win_$LY; k 97 1.2; done
  A --es voltcap_test_layout CHAN; sleep 2; A --es voltcap_test_pref 'layout_colors={"CHAN":{"p":"CUSTOM","acc":-7453480}}'; sleep 1.5
  A --es voltcap_test_settings Theme; sleep 2.5; shot win_CHAN_purple; k 97 1.2
  A --es voltcap_test_game "Mario Kart 64"; sleep 1; k 96 1.6; shot win_CHAN_purple_popup; k 97 1.2
  A --ez voltcap_test_preview false; sleep 0.5; log "win done" ;;
media) for g in "Mario_Kart_64" "Crash_Bandicoot" "Pokemon_-_Emerald_Version" "Shadow_of_the_Colossus"; do for y in 0 140; do
    k 97 0.8; A --es voltcap_test_inspect "$g||$y|0"; sleep 3.2; shot media_${g}_y$y; done; done
  k 97 0.8; for LY in SHELF STACKS; do A --es voltcap_test_layout $LY; sleep 3; shot media_home_$LY; done; log "media done" ;;
esac; done
k 97 0.8; A --es voltcap_test_snapshot restore; sleep 3
QP=$(pidof $P)
log "restore: $(cat $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
log "launchesBlocked=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked') fatal=$(logcat -d | grep -c 'FATAL EXCEPTION') prefLines=$(logcat -d | grep -c 'VoltcapTest: pref')"
log "aynCalls(qa pid $QP)=$(logcat -d | grep -iE 'PServer|VendorAdapter|RootShell' | grep -c " $QP ")"
logcat -d | grep 'VoltcapTest: pref' > $O/prefs.txt
log "end $(date +%T)"; echo DONE >> $L
