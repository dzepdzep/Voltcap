#!/system/bin/sh
# Update 513: Ken Burns drift comparison in Voltcap QA only. sh kb513.sh <mode 0|1|2> <tag> [layout] [rec]
# mode 0 = every frame (511), 1 = 512 steps, 2 = paced. Writes the mode file in the QA app's own folder (read when the layout
# is entered; nothing is restarted, so the owner's Voltcap never comes to the front), enters <layout>, then three 6 s windows (CPU, frames, janky, p90) with framestats dumped twice a second, and PSS.
# rec=1: instead records 10 s of the drift with screenrecord (no CPU windows).
P=com.shelf.launcher.qa; M=$1; T=$2; L=${3:-TILES}; R=${4:-0}
D=/sdcard/Android/data/$P/files; O=/data/local/tmp/kb513; mkdir -p $O
echo $M > $D/qa_kb_mode; rm -f $O/fs_${T}_w*.txt
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout SHELF >/dev/null 2>&1; sleep 6
# A cold start opens "Who's playing?" (it animates and covers the home): B keeps the player.
grep -q "PICKER $(pidof $P)" $D/voltcap_qa_screen 2>/dev/null && { input keyevent 97; sleep 2; }
grep -q "PICKER $(pidof $P)" $D/voltcap_qa_screen 2>/dev/null && { echo "$T picker still up"; exit 1; }
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout $L --es voltcap_test_game "Mario Kart 64" >/dev/null 2>&1
sleep 1; input keyevent 59; sleep 2
if [ "$R" = "1" ]; then
  screenrecord --time-limit 10 --bit-rate 20000000 $O/rec_${T}.mp4
  echo "$T rec done"; exit 0
fi
for w in 1 2 3; do
  dumpsys gfxinfo $P reset >/dev/null
  ( for i in 1 2 3 4 5 6 7 8 9 10; do dumpsys gfxinfo $P framestats | grep -E '^(Flags|0,|1,)' >> $O/fs_${T}_w$w.txt; sleep 0.5; done ) &
  C=$(top -b -n 2 -d 6 -p $(pidof $P) | grep " $P" | tail -1 | awk '{print $9}')
  wait
  G=$(dumpsys gfxinfo $P)
  F=$(echo "$G" | grep "Total frames rendered" | head -1 | awk '{print $4}')
  J=$(echo "$G" | grep "Janky frames:" | head -1 | awk '{print $3,$4}')
  N=$(echo "$G" | grep "90th percentile" | head -1 | awk '{print $3}')
  echo "$T $L win$w cpu=${C}% frames6s=$F janky=$J p90=$N"
done
echo "$T $L pssKB=$(dumpsys meminfo $P | grep 'TOTAL PSS' | awk '{print $3}')"
