#!/system/bin/sh
# Update 512: the hero Ken Burns drift after entering a layout (QA app only). Shelf first, then <layout>; a no-op key;
# three 6 s windows (CPU %, frames, janky, 90th pct frame time) + PSS. sh kb512.sh <tag> <layout>
P=com.shelf.launcher.qa; T=$1; L=${2:-TILES}
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout SHELF >/dev/null 2>&1; sleep 5
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout $L --es voltcap_test_game "Mario Kart 64" >/dev/null 2>&1
sleep 1; input keyevent 59; sleep 2
for w in 1 2 3; do
  dumpsys gfxinfo $P reset >/dev/null
  C=$(top -b -n 2 -d 6 -p $(pidof $P) | grep " $P" | tail -1 | awk '{print $9}')
  G=$(dumpsys gfxinfo $P)
  F=$(echo "$G" | grep "Total frames rendered" | head -1 | awk '{print $4}')
  J=$(echo "$G" | grep "Janky frames:" | head -1 | awk '{print $3,$4}')
  N=$(echo "$G" | grep "90th percentile" | head -1 | awk '{print $3}')
  echo "$T $L win$w cpu=${C}% frames6s=$F janky=$J p90=$N"
done
echo "$T $L pssKB=$(dumpsys meminfo $P | grep 'TOTAL PSS' | awk '{print $3}')"
