#!/system/bin/sh
# 682bd-3 (owner 25 Nov 23:41, "TUNER VISUAL APPROVAL - TARGETED PREVIEW FIRST"): screenshots for the owner's review only - the
# R1 Heat guard tile, its sheet with each of the three presets, the tuner settings page, a game's own Underclock Off,
# device-wide AutoTDP Balanced / With power, and a simulated heat guard intervention. LEVEL 1: the isolated QA app only, on
# its SIMULATED tuner (TuneSim - nothing of the device's clocks, fan, thermal files or settings is touched, nothing launched).
#   sh tunerprev.sh TAG [SCALE]   SCALE 1.3 = Voltcap's own text at x1.3 (voltcap_test_scale; Android's font size untouched)
# Keys / taps only while the QA app has the focus; at the end the QA app's tuner settings and its scale are put back.
. /data/local/tmp/vqa/qaguard.sh
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; SC=${2:-1.0}; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
S() { A --es voltcap_test_tunesim "$1"; sleep ${2:-1.5}; }
front() { dumpsys window | grep mCurrentFocus | grep -q "$P/"; }
log() { echo "$(date +%T) $*" >> $L; }
fin() { front && input keyevent KEYCODE_BUTTON_B; sleep 1; S end; S reset; S off; A --es voltcap_test_scale off; sleep 1; echo DONE >> $L; exit ${1:-0}; }
key() { front || { log "STOP: QA app not in front - nothing pressed"; fin 4; }; input keyevent $1; sleep ${2:-1.2}; }
tap() { front || { log "STOP: QA app not in front - nothing pressed"; fin 4; }; input tap $1 $2; sleep ${3:-1.2}; }
hold() { front || { log "STOP: QA app not in front - nothing pressed"; fin 4; }; input swipe $1 $2 $1 $2 900; sleep 2; }
shot() { screencap -p $O/$1.png; log "shot $1"; }
clip() { A --es voltcap_test_clip ${TAG}_$1; sleep 2.5; cp $F/qa_clip/${TAG}_$1.json $O/$1.json 2>/dev/null; }
ctl() { grep -o "\"label\":\"$2[^\"]*\",\"kind\":\"control\",\"full\":\[[0-9,-]*\]" $O/$1.json 2>/dev/null | head -1; }
mid() { echo "$1" | sed 's/.*full":\[//;s/\]//' | awk -F, '{print int($1+$3/2), int($2+$4/2)}'; }
dumpsys power | grep -q "mWakefulness=Awake" || { log "STOP asleep"; exit 4; }
[ "$(cut -d' ' -f1 $F/voltcap_qa_screen)" = HOME ] || { log "STOP: not on the QA home ($(cut -c1-20 $F/voltcap_qa_screen))"; exit 4; }
[ "$SC" = 1.0 ] || { A --es voltcap_test_scale "font=$SC"; sleep 2; }
# 1. device-wide AutoTDP Balanced, Underclock With power; heat guard Balanced 85 C; no game
S on 3; S "global:AUTO|BALANCED"; S "heat:on|BALANCED|85"; S temp:55 1
A --es voltcap_test_settings Performance; sleep 3; shot 1_tuner_device; clip 1
key KEYCODE_DPAD_DOWN 0.6; key KEYCODE_DPAD_DOWN 0.6; key KEYCODE_DPAD_DOWN 0.6; key KEYCODE_DPAD_DOWN 0.6; shot 1b_tuner_device_down
key KEYCODE_BUTTON_B 1.5
# 2. a game with its own profile: Underclock Off (AutoTDP off)
S "own:com.qa.tunegame|OFF|none"; S "launch:com.qa.tunegame|Tune Game|com.qa.tunegame" 3
A --es voltcap_test_settings Performance; sleep 3; shot 2_tuner_game_off; clip 2
key KEYCODE_BUTTON_B 1.5
# 3. simulated heat: 97 C (the guard steps in after its trigger time)
S temp:97 1; sleep 25
A --es voltcap_test_settings Performance; sleep 3; shot 3_tuner_heat; clip 3
key KEYCODE_BUTTON_B 1.5
# 4. R1 panel, Device tab: the Heat guard tile while it restricts; its sheet with each preset
key KEYCODE_BUTTON_R1 1.8; key KEYCODE_BUTTON_R1 1.5; clip 4
# Device tab not shown (RP Classic: the panel's R1 does not switch its tab; KONKR x1.3: it can land too early): one tap on
# the Device tab once the panel has settled, GUARDED - on 26 Nov an unguarded tap during the slide-in hit the volume slider
# (D402); the media volume is read before and after, any change is put back at once and the run stops.
vol() { cmd media_session volume --get --stream 3 2>/dev/null | sed -n 's/.*volume is \([0-9]*\).*/\1/p' | tail -1; }
gtap() { v0=$(vol); tap $1 $2 1.5; v1=$(vol); [ "$v0" = "$v1" ] && return 0
  [ -n "$v0" ] && cmd media_session volume --stream 3 --set $v0 >/dev/null 2>&1; log "STOP: volume moved $v0 -> $v1 by a tap - put back to $v0"; fin 4; }
if ! grep -q '"label":"Airplane\|"label":"Android settings\|"label":"Heat guard' $O/4.json; then
  sleep 1.5; clip 4; d=$(ctl 4 "Device"); log "Device tab: $d"
  [ -n "$d" ] && { gtap $(mid "$d"); clip 4; }
fi
shot 4_r1_device
h=$(ctl 4 "Heat guard")
if [ -z "$h" ]; then m=$(ctl 4 "More tiles"); [ -n "$m" ] && { tap $(mid "$m") 1.5; clip 4; shot 4_r1_device_more; h=$(ctl 4 "Heat guard"); }; fi
log "tile: $h"
if [ -n "$h" ]; then
  hold $(mid "$h"); clip 5; shot 5_sheet_balanced
  for pr in Gentle Aggressive Balanced; do c=$(ctl 5 "$pr"); log "preset $pr: $c"
    [ -n "$c" ] && { tap $(mid "$c") 1.5; shot 5_sheet_$(echo $pr | tr A-Z a-z); }; done
  key KEYCODE_BUTTON_B 1.2
fi
key KEYCODE_BUTTON_B 1.2
fin 0
