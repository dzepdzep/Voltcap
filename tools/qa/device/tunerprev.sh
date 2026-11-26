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
# (KONKR x1.3: the panel's R1 tab switch can land before the panel is ready - then R1 once more. NEVER a coordinate tap in
# the panel: on 26 Nov a tap aimed at the Device tab during the slide-in hit the volume slider and set the media volume (D402).)
grep -q '"label":"Airplane\|"label":"Android settings\|"label":"Heat guard' $O/4.json || { key KEYCODE_BUTTON_R1 1.5; clip 4; }
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
