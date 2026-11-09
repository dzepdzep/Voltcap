#!/system/bin/sh
# Update 513: Game shelf / Case stacks "incoming art flashes before the case moves" — QA app only. Records the screen while
# moving: 3 single steps right, 3 left, a burst of 5 rights, then A (window) and B (close). Keys only while the QA app has focus.
#   sh flash513.sh TAG [LAYOUT]   -> /data/local/tmp/flash513/<TAG>_<LAYOUT>.mp4 + key times (uptime ms) in <TAG>_<LAYOUT>.keys
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; L=${2:-SHELF}; O=/data/local/tmp/flash513; mkdir -p $O
K=$O/${TAG}_$L.keys; : > $K
k() { f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) ;; *) echo "ABORT focus $f" >> $K; exit 2;; esac
  echo "$(cut -d' ' -f1 /proc/uptime) $1" >> $K; input keyevent $1; sleep ${2:-0.7}; }
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout $L --es voltcap_test_game "Mario Kart 64" >/dev/null 2>&1; sleep 4
grep -q "^PICKER $(pidof $P)" $F/voltcap_qa_screen 2>/dev/null && k 97 1.5
screenrecord --time-limit 14 $O/${TAG}_$L.mp4 & sleep 1.0
echo "$(cut -d' ' -f1 /proc/uptime) recstart" >> $K
k 22 1.3; k 22 1.3; k 22 1.3; k 21 1.3; k 21 1.3; k 21 1.3
for i in 1 2 3 4 5; do k 22 0.12; done; sleep 1.4
k 96 1.6; k 97 1.4
wait; echo done >> $K
