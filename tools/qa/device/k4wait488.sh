# Update 488 (K4) — generated spines: how many are shown WITHOUT their name ("spine wait", QaTrace) going out and coming back.
# sh k4wait488.sh LAYOUT CATEGORY STEPS. Preview hooks only; nothing saved or launched. A cold run first (process restarted
# by the caller), then the same walk again warm. Output: per phase counts on stdout + /sdcard/pc/k4w/<L>_<C>.txt
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; L=$1; C=$2; N=${3:-15}; OUT=/sdcard/pc/k4w; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
input keyevent 224; A --ez voltcap_test_preview true --es voltcap_test_layout $L; sleep 4
A --es voltcap_test_category "$C"; sleep 4; input keyevent 224; sleep 2
logcat -c
# (The frame-pacing trace fills the log buffer in seconds: the spine lines are filtered while they happen.)
logcat -s VoltcapQA:E | grep --line-buffered -E "PHASE|spine (wait|made|src|ahead)" > $OUT/${L}_$C.raw &
LP=$!
walk() { log -t VoltcapQA -p e "PHASE $1-out"; i=0; while [ $i -lt $N ]; do input keyevent KEYCODE_DPAD_RIGHT; i=$((i+1)); done; sleep 2
         log -t VoltcapQA -p e "PHASE $1-back"; i=0; while [ $i -lt $N ]; do input keyevent KEYCODE_DPAD_LEFT; i=$((i+1)); done; sleep 2; }
walk run1; walk run2
sleep 1; pkill -f "logcat -s VoltcapQA" 2>/dev/null; wait $LP 2>/dev/null; sleep 1  # logcat first: grep then flushes at EOF
sed -E 's/^.*VoltcapQA: //' $OUT/${L}_$C.raw > $OUT/${L}_$C.txt; rm -f $OUT/${L}_$C.raw
# wait-noname: shown without its name (the flicker); wait-low: its small named copy shown meanwhile (update 488)
awk '/PHASE/{p=$NF} /spine ahead/{a[p]++} /spine wait/{ if ($0 ~ /low=true/) l[p]++; else w[p]++ } /spine made/{m[p]++} END{for (k in a) print k, "ahead", a[k]; for (k in w) print k, "wait-noname", w[k]; for (k in l) print k, "wait-low", l[k]; for (k in m) print k, "made", m[k]}' $OUT/${L}_$C.txt | sort
