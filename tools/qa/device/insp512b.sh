#!/system/bin/sh
# Update 512: Inspect touch behaviour with ONE finger (adb `input` has one pointer — no pinch / two-finger pan here).
# (sh insp512.sh TAG [x%] [y%] — where to drag; default the middle.) Tiles home -> A (game window) -> tap its Inspect control (found in QaClip's dump) -> a recording of: idle motion,
# a one-finger drag, the 3 s hold, the 360 ms settle-back and the idle motion after; then Y (reset) recorded. QA app only.
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; T=$1; O=$F/$T; mkdir -p $O; rm -f $O/*
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout TILES --es voltcap_test_game "Mario Kart 64" >/dev/null 2>&1; sleep 3.5
input keyevent 96; sleep 1.8
rm -f $F/qa_clip/${T}_w.json; echo "${T}_w" > $F/qa_clip_req; sleep 1.5
r=$(grep -oE '"label":"Inspect","kind":"control","full":\[[0-9,-]*\],"shown":\[[0-9-]*,[0-9-]*,[0-9]*,[0-9]*\]' $F/qa_clip/${T}_w.json | head -1 | sed 's/.*"shown":\[//;s/\]//')
[ -z "$r" ] && { echo "no Inspect control"; input keyevent 97; exit 1; }
set -- $(echo $r | tr ',' ' '); input tap $(( $1 + $3 / 2 )) $(( $2 + $4 / 2 )); sleep 2.5
W=$(wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); X=$(( ${W%x*} * ${2:-50} / 100 )); Y=$(( ${W#*x} * ${3:-50} / 100 ))
screenrecord --time-limit 9 $O/drag.mp4 & sleep 1.0
logcat -c
input swipe $(( X - 150 )) $Y $(( X + 150 )) $Y 450; D=$(date +%s)
wait
screenrecord --time-limit 4 $O/reset.mp4 & sleep 0.8
input swipe $(( X + 100 )) $Y $(( X - 100 )) $(( Y + 60 )) 300; sleep 0.6; input keyevent 100
wait
logcat -d | grep -E "VoltcapQA: (inspect|LAUNCH)|FATAL EXCEPTION" > $O/log.txt
input keyevent 97; sleep 1; input keyevent 97; sleep 1
echo "insp done $(ls $O)"
