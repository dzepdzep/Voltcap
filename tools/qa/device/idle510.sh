#!/system/bin/sh
# Update 510: idle cost of a home layout (QA app only). usage: sh idle510.sh <tag> <wait_s> <layouts...>
# After the layout opens, no input for <wait_s> s; then 6 s of: frames rendered (gfxinfo), CPU % (top), PSS.
P=com.shelf.launcher.qa; T=$1; shift; W=$1; shift
for L in $*; do
  am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout $L --es voltcap_test_game "Mario Kart 64" >/dev/null 2>&1
  sleep $W
  dumpsys gfxinfo $P reset >/dev/null
  C=$(top -b -n 2 -d 6 -p $(pidof $P) | grep " $P" | tail -1 | awk '{print $9}')
  F=$(dumpsys gfxinfo $P | grep "Total frames rendered" | head -1 | awk '{print $4}')
  J=$(dumpsys gfxinfo $P | grep "Janky frames:" | head -1 | awk '{print $3,$4}')
  M=$(dumpsys meminfo $P | grep "TOTAL PSS" | awk '{print $3}')
  echo "$T $L wait=${W}s cpu=${C}% frames6s=$F janky=$J pssKB=$M"
done
