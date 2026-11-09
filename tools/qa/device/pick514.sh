#!/system/bin/sh
# Update 514: "Who's playing?" geometry on this device — isolated Voltcap QA app ONLY (sample profiles: nothing is switched or saved).
#   sh pick514.sh TAG [nav]   → <files>/TAG/: one screenshot + the picker window's frame per case, log.txt
#   nav = on: the windows ask for the navigation bar shown, as the owner's Voltcap on a Retroid (voltcap_test_navshown).
# Cases: profile counts 1 / 3 / 5 / 8, long names, no photo, locked; every look; then D-pad right ×3 + B closes (focus check).
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; NAV=${2:-off}; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
focus() { dumpsys window | grep mCurrentFocus | tail -1; }
k() { case "$(focus)" in *com.shelf.launcher.qa*) ;; *) log "ABORT: focus is not the QA app: $(focus)"; echo DONE >> $L; exit 2;; esac; input keyevent $1; sleep ${2:-0.6}; }
frames() { dumpsys window windows | awk '/Window #[0-9]+ Window\{/{w=($0 ~ /shelf\.launcher\.qa/)} w && /mAttrs=/{a=$0} w && /Frames:/{sub(/^ +/,"");print a" | "$0}' | sed 's/^ *mAttrs={//' ; }
log "start $(date +%T) $(getprop ro.product.model) $(wm size | tail -1) $(wm density | tail -1)"
log "real=$(pidof com.shelf.launcher) qa=$(dumpsys package $P | grep -m1 versionName)"
A --ez voltcap_test_nolaunch true; sleep 3
A --es voltcap_test_navshown $NAV; sleep 1; log "navshown=$NAV"
for LK in AURORA CONSOLE NEON OLED THEME MIDNIGHT; do
  A --es voltcap_test_pickerlook $LK; sleep 0.5
  for SP in 1 3:long 5:nophoto 8 8:long 2:locked; do
    A --es voltcap_test_profiles "$SP"; sleep 2.2
    N=${LK}_$(echo $SP | tr ':' '_'); screencap -p $O/$N.png; log "case $N"; frames >> $L
    k 97 0.8   # B closes the preview (BACK is ignored at start-up by design)
  done
done
A --es voltcap_test_pickerlook AURORA; sleep 0.5; A --es voltcap_test_profiles "3"; sleep 2
k 22; k 22; k 22; screencap -p $O/dpad_right3.png; k 97 1; log "after B: $(focus)"
A --es voltcap_test_pickerlook off; sleep 0.5; A --es voltcap_test_navshown off; sleep 0.5
log "end $(date +%T) real=$(pidof com.shelf.launcher)"; echo DONE >> $L
