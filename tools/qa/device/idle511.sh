#!/system/bin/sh
# Update 511: idle cost after a real input (QA app only). usage: sh idle511.sh <tag> <wait_s> <layouts...>
# Opens the layout, presses a no-op key (SHIFT_LEFT: counts as input, moves nothing), waits <wait_s> s with no input,
# then 6 s of: CPU % (top), frames rendered + janky (gfxinfo), PSS.
P=com.shelf.launcher.qa; T=$1; shift; W=$1; shift
for L in $*; do
  am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout $L --es voltcap_test_game "Mario Kart 64" >/dev/null 2>&1
  sleep 3; input keyevent 59; sleep $W
  dumpsys gfxinfo $P reset >/dev/null
  C=$(top -b -n 2 -d 6 -p $(pidof $P) | grep " $P" | tail -1 | awk '{print $9}')
  F=$(dumpsys gfxinfo $P | grep "Total frames rendered" | head -1 | awk '{print $4}')
  J=$(dumpsys gfxinfo $P | grep "Janky frames:" | head -1 | awk '{print $3,$4}')
  M=$(dumpsys meminfo $P | grep "TOTAL PSS" | awk '{print $3}')
  echo "$T $L wait=${W}s cpu=${C}% frames6s=$F janky=$J pssKB=$M"
done
