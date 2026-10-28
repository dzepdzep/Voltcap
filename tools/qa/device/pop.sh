# VoltCap QA — record two moves in a home layout (preview only): sh pop.sh LAYOUT  → /data/local/tmp/pop_LAYOUT.mp4
# Needs the test hooks on and Voltcap's home in front. Presses only R twice, then L twice (back where it was).
# Stops (no key pressed) when "Who's playing?" is showing.
L=$1; o=/data/local/tmp/pop_$L.mp4; rm -f $o
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview true --es voltcap_test_layout $L > /dev/null 2>&1; sleep 4
[ "$(sh /data/local/tmp/vqa/picker.sh)" = PICKER ] && { echo "$L SKIPPED: picker"; exit 0; }
(screenrecord --time-limit 4 --size 960x540 --bit-rate 6000000 $o &); sleep 0.7
sh /data/local/tmp/vqa/vqa.sh seq "R" 100 > /dev/null; sleep 1.2
sh /data/local/tmp/vqa/vqa.sh seq "R" 100 > /dev/null; sleep 2.6
sh /data/local/tmp/vqa/vqa.sh seq "L L" 400 > /dev/null
ls -la $o | awk '{print $5, $NF}'
