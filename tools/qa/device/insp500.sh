#!/system/bin/sh
# Update 500: Inspect touch check in the isolated QA app (it cannot start a game). Real fingers are needed: the Thor's
# touchscreen ignores injected events (sendevent) and `input` has one pointer, so this opens the piece and records what
# the app saw while the owner pinches / pans / turns / presses Y. Usage: insp500.sh <Game_name> [seconds]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; O=$F/insp500; T=${2:-40}
mkdir -p $O; logcat -c
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_game "$1" >/dev/null; sleep 2.5
input keyevent 96; sleep 3          # A: the game window
input tap 804 804; sleep 3           # its Inspect button (Thor, 1920x1080)
screencap -p $O/0_open.png
echo "Inspect is open: pinch, pan with two fingers, turn with one, press Y - recording ${T}s"
sleep $T; screencap -p $O/1_end.png
logcat -d | grep -E "VoltcapQA: (inspect|LAUNCH)|FATAL EXCEPTION" > $O/log.txt
cat $O/log.txt
