# VoltCap QA — controller walk (QaWalk): real pad presses, Voltcap logs focus + the controls of each screen.
#   sh walk.sh NAME "KEYS" [GAP_MS]     e.g. sh walk.sh R1 "R1 D D D D R R R B" 450   → out/NAME_qa.txt → tools/walkcheck.py
#   The walk starts on Voltcap's home screen (hooks on → ready); it ends with B×4 and the hooks off again.
n=$1; keys=$2; gap=${3:-450}
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "walk $n ABORTED (not on home)"; exit 1; }
# The walk can press A and move sliders: Voltcap's settings, the brightness and the volumes are saved first and put back
# afterwards (pass 3: a walk moved the R1 brightness / volume sliders).
B0=$(settings get system screen_brightness); BM=$(settings get system screen_brightness_mode)
V3=$(cmd media_session volume --stream 3 --get 2>/dev/null | grep -o "volume is [0-9]*" | grep -o "[0-9]*$")
am start -n com.shelf.launcher/.MainActivity --es voltcap_test_snapshot save > /dev/null 2>&1; sleep 1
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_walk true > /dev/null 2>&1; sleep 1.5
logcat -c
for k in $keys; do log -p e -t VoltcapQA "MARK key $k"; sh vqa.sh seq "$k" $gap > /dev/null; done; sleep 1
logcat -d -s VoltcapQA > out/${n}_qa.txt
sh vqa.sh seq "B B B B" 300
am start -n com.shelf.launcher/.MainActivity --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1
settings put system screen_brightness_mode $BM; settings put system screen_brightness $B0
[ -n "$V3" ] && cmd media_session volume --stream 3 --set $V3 > /dev/null 2>&1
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200
echo "walk $n done: $(grep -c ' walk key=' out/${n}_qa.txt) steps"
