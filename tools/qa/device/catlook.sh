# VoltCap QA (pass 4) — category game looks: a console with its own game look, the others on the default; L2 / R2 into
# and out of it recorded (half size, all frames) for frames.py. Session only: preview (no appearance saved) and the
# settings snapshot around it.
#   sh catlook.sh RUN CONSOLE LOOK NEXT   e.g. sh catlook.sh CG GameCube CASE N64
run=$1; c=$2; l=$3; nx=$4
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "catlook ABORTED"; exit 1; }
A="am start -n com.shelf.launcher/.MainActivity"
$A --es voltcap_test_snapshot save > /dev/null 2>&1; sleep 1
$A --ez voltcap_test_preview true > /dev/null 2>&1; sleep 1
$A --es voltcap_test_pref "cat_game_looks={\"P:$c\":\"$l\"}" > /dev/null 2>&1; sleep 0.5
$A --es voltcap_test_category $nx > /dev/null 2>&1; sleep 3; screencap -p out/${run}_1_${nx}.png
$A --es voltcap_test_category $c > /dev/null 2>&1; sleep 3; screencap -p out/${run}_2_${c}.png
$A --es voltcap_test_category $nx > /dev/null 2>&1; sleep 3; screencap -p out/${run}_3_${nx}.png
logcat -c
(nohup screenrecord --size 960x540 --time-limit 7 out/${run}.mp4 > /dev/null 2>&1 &)
sleep 1; sh vqa.sh seq "L2" 2000 > /dev/null; sh vqa.sh seq "R2" 2000 > /dev/null; sleep 2
logcat -d -s VoltcapQA > out/${run}_qa.txt
$A --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200; echo "catlook $run done"
