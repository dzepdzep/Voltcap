#!/system/bin/sh
# Update 510: Shelf bob resume after the idle settle (QA app only). A no-op key (SHIFT_LEFT) after 15 s at rest;
# screenrecord across the press (look for a jump), frames while resumed, then frames after it settles again.
P=com.shelf.launcher.qa; K=${1:-59}
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout SHELF --es voltcap_test_game "Mario Kart 64" >/dev/null 2>&1
sleep 15
screenrecord --time-limit 4 /sdcard/Download/r510.mp4 &
sleep 1; input keyevent $K; T0=$(date +%s)
wait
dumpsys gfxinfo $P reset >/dev/null; sleep 4
F1=$(dumpsys gfxinfo $P | grep "Total frames rendered" | head -1 | awk '{print $4}')
J1=$(dumpsys gfxinfo $P | grep "Janky frames:" | head -1 | awk '{print $3,$4}')
sleep 9
dumpsys gfxinfo $P reset >/dev/null; sleep 6
F2=$(dumpsys gfxinfo $P | grep "Total frames rendered" | head -1 | awk '{print $4}')
echo "resume key=$K framesWhileResumed4s=$F1 janky=$J1 framesAfterResettle6s=$F2"
