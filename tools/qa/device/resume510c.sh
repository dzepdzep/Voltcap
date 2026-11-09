#!/system/bin/sh
# Update 510 (c): frames per 3 s window after a no-op key at rest on Game shelf (QA app only).
P=com.shelf.launcher.qa; K=${1:-59}
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout SHELF --es voltcap_test_game "Mario Kart 64" >/dev/null 2>&1
sleep 15
dumpsys gfxinfo $P reset >/dev/null; sleep 3
O="pre:$(dumpsys gfxinfo $P | grep 'Total frames rendered' | head -1 | awk '{print $4}')"
input keyevent $K; S=$(date +%s)
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13; do
  dumpsys gfxinfo $P reset >/dev/null; sleep 3
  O="$O t+$(( $(date +%s) - S ))s:$(dumpsys gfxinfo $P | grep 'Total frames rendered' | head -1 | awk '{print $4}')"
done
echo "$O"
