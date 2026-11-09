#!/system/bin/sh
# Update 511: what draws <wait_s> s after an input on a layout (QA app only, profileable build). sh perf511.sh <tag> <layout> <wait_s>
P=com.shelf.launcher.qa; T=$1; L=$2; W=$3
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout $L --es voltcap_test_game "Mario Kart 64" >/dev/null 2>&1
sleep 3; input keyevent 59; sleep $W
simpleperf record --app $P -e cpu-clock -f 2000 -g --duration 6 -o /data/local/tmp/$T.data >/dev/null 2>&1
simpleperf report -i /data/local/tmp/$T.data --comms $P --children --sort symbol --percent-limit 1 2>/dev/null | grep -E "com\.shelf|^Samples|Choreographer.doFrame" | head -60 > /data/local/tmp/$T.txt
rm -f /data/local/tmp/$T.data
