#!/system/bin/sh
# 682bd: LEVEL 1, the isolated QA app on its SIMULATED tuner hardware (TuneSim): the R1 panel's Heat guard tile while the guard
# restricts (Device tab, its More page when the owner's tiles fill the first), its hold sheet, and A twice (off, on again).
# Keys / taps only while the QA app has the focus; the QA app's tuner settings are put back at the end (reset). Starts on the
# QA app's home (not the picker). Output: /sdcard/pc/r1heat (log, screenshots, the panel's layout dumps).
. /data/local/tmp/vqa/qaguard.sh
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; O=/sdcard/pc/r1heat; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
S() { A --es voltcap_test_tunesim "$1"; sleep ${2:-1.5}; }
front() { dumpsys window | grep mCurrentFocus | grep -q "$P/"; }
log() { echo "$(date +%T) $*" >> $L; }
fin() { S end; S reset; S off; echo DONE >> $L; exit ${1:-0}; }
key() { front || { log "STOP: QA app not in front - nothing pressed"; fin 4; }; input keyevent $1; sleep ${2:-1.2}; }
tap() { front || { log "STOP: QA app not in front - nothing pressed"; fin 4; }; input tap $1 $2; sleep ${3:-1.2}; }
hold() { front || { log "STOP: QA app not in front - nothing pressed"; fin 4; }; input swipe $1 $2 $1 $2 900; sleep 2; }
clip() { A --es voltcap_test_clip r1heat_$1; sleep 2.5; cp $F/qa_clip/r1heat_$1.json $O/; }
ctl() { grep -o "\"label\":\"$2[^\"]*\",\"kind\":\"control\",\"full\":\[[0-9,-]*\]" $O/r1heat_$1.json | head -1; }
mid() { echo "$1" | sed 's/.*full":\[//;s/\]//' | awk -F, '{print int($1+$3/2), int($2+$4/2)}'; }
[ "$(cut -d' ' -f1 $F/voltcap_qa_screen)" = HOME ] || { log "STOP: not on the QA home ($(cut -c1-20 $F/voltcap_qa_screen))"; exit 4; }
S on 3; S "heat:on|BALANCED|85"; S "launch:com.qa.tunegame|Tune Game|com.qa.tunegame" 3; S temp:97 1; sleep 30
key KEYCODE_BUTTON_R1 1.8; clip a
d=$(ctl a "Device"); [ -n "$d" ] || { log "no Device tab"; key KEYCODE_BUTTON_B; fin 4; }
# (KONKR: a tap on the tab during the panel's slide-in missed it - the panel's own L1 / R1 switch is used: R1 = Device.)
key KEYCODE_BUTTON_R1 1.5; clip b; screencap -p $O/b_device_tab.png
grep -q '"label":"Airplane\|"label":"Android settings\|"label":"Heat guard' $O/r1heat_b.json || { tap $(mid "$d") 1.5; clip b; screencap -p $O/b_device_tab.png; }
h=$(ctl b "Heat guard"); log "tile: $h"
if [ -z "$h" ]; then m=$(ctl b "More tiles"); [ -n "$m" ] && { tap $(mid "$m") 1.5; clip c; screencap -p $O/c_more.png; h=$(ctl c "Heat guard"); log "tile (More): $h"; }; fi
if [ -n "$h" ]; then hold $(mid "$h"); screencap -p $O/d_sheet.png; clip d
  log "sheet: $(grep -o '"label":"[^"]*"' $O/r1heat_d.json | grep -iE 'heat|now|limit|°C|Balanced|Gentle|Aggressive|restrict' | head -10 | tr '\n' ' ')"
  key KEYCODE_BUTTON_B 1.2
  tap $(mid "$h") 2; clip e; log "after A: $(ctl e "Heat guard")"; tap $(mid "$h") 2; clip f; log "after A again: $(ctl f "Heat guard")"; fi
key KEYCODE_BUTTON_B 1.2
fin 0
