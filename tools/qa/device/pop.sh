# VoltCap QA — record two moves in a home layout (preview only): sh pop.sh LAYOUT  → /data/local/tmp/pop_LAYOUT.mp4
# Needs the test hooks on and Voltcap's home in front. Presses only R twice, then L twice (back where it was).
# Stops (no key pressed) unless the screen is verified HOME (picker, black or unknown screens skip). Needs guard.sh + picker.sh next to vqa.sh.
L=$1; o=/data/local/tmp/pop_$L.mp4; rm -f $o
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview true --es voltcap_test_layout $L > /dev/null 2>&1; sleep 4
cd ${VQA:-/data/local/tmp/vqa}; . ./guard.sh; st=$(screen_state)
[ "$st" = HOME ] || { echo "$L SKIPPED: not on the home screen ($st)"; exit 0; }
(screenrecord --time-limit 4 --size 960x540 --bit-rate 6000000 $o &); sleep 0.7
sh ${VQA:-/data/local/tmp/vqa}/vqa.sh seq "R" 100 > /dev/null; sleep 1.2
sh ${VQA:-/data/local/tmp/vqa}/vqa.sh seq "R" 100 > /dev/null; sleep 2.6
sh ${VQA:-/data/local/tmp/vqa}/vqa.sh seq "L L" 400 > /dev/null
ls -la $o | awk '{print $5, $NF}'
