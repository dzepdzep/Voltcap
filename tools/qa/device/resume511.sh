#!/system/bin/sh
# Update 511: resume after the 10 s settle on <layout> (QA app only): a key at rest, recorded (look for a jump),
# then frames per 3 s window for 30 s (resumed effects, settle again 10 s later). sh resume511.sh <tag> <layout>
P=com.shelf.launcher.qa; T=$1; L=$2
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout $L --es voltcap_test_game "Mario Kart 64" >/dev/null 2>&1
sleep 3; input keyevent 59; sleep 15
screenrecord --time-limit 4 /sdcard/Download/$T.mp4 &
sleep 1; input keyevent 59; S=$(date +%s); wait
O="key"
for i in 1 2 3 4 5 6 7 8 9; do
  dumpsys gfxinfo $P reset >/dev/null; sleep 3
  O="$O t+$(( $(date +%s) - S ))s:$(dumpsys gfxinfo $P | grep 'Total frames rendered' | head -1 | awk '{print $4}')"
done
echo "$T $L $O"
