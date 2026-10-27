# VoltCap QA — controller explorer (QaExplore): Voltcap drives the D-pad through a whole area by itself and logs every
# page in the walk format. Settings, brightness and volume are saved first and put back afterwards.
#   sh explore.sh NAME "SELECT" [MAX_S]     (SELECT = all settings; R1 = quick panel; START = game options)
#   → out/NAME_qa.txt → tools/walkcheck.py
n=$1; start=$2; max=${3:-900}
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "explore $n ABORTED (not on home)"; exit 1; }
B0=$(settings get system screen_brightness); BM=$(settings get system screen_brightness_mode)
V3=$(cmd media_session volume --stream 3 --get 2>/dev/null | grep -o "volume is [0-9]*" | grep -o "[0-9]*$")
am start -n com.shelf.launcher/.MainActivity --es voltcap_test_snapshot save > /dev/null 2>&1; sleep 1
logcat -c; logcat -G 16M 2>/dev/null
am start -n com.shelf.launcher/.MainActivity --es voltcap_test_explore "$start" > /dev/null 2>&1
t=0; while [ $t -lt $max ]; do sleep 5; t=$((t+5)); logcat -d -s VoltcapQA | grep -qE "explore (done|error)" && break; done
am start -n com.shelf.launcher/.MainActivity --es voltcap_test_explore stop > /dev/null 2>&1; sleep 1
logcat -d -s VoltcapQA > out/${n}_qa.txt
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_walk false > /dev/null 2>&1
sh vqa.sh seq "B B B B B" 300
am start -n com.shelf.launcher/.MainActivity --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1
settings put system screen_brightness_mode $BM; settings put system screen_brightness $B0
[ -n "$V3" ] && cmd media_session volume --stream 3 --set $V3 > /dev/null 2>&1
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200
echo "explore $n: $(grep -c 'MARK key' out/${n}_qa.txt) keys, $(grep -o 'explore .*' out/${n}_qa.txt | tail -1) after ${t}s"
