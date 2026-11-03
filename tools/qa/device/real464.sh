# Update 464: the REAL cartridge photo path as you open it — game window › Inspect (case) › open › take the cartridge out —
# then turned by the same slow touch drags. Identical script on the RC and on staging, for pixel comparison.
# usage: sh real464.sh OUT NAME
P=com.shelf.launcher; OUT=$1; N=$2; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
f=$(echo "$N" | tr -c 'A-Za-z0-9\n' '_')
input keyevent KEYCODE_WAKEUP
input keyevent 97; sleep 1; input keyevent 97; sleep 1
A --es voltcap_test_game "$N"; sleep 4
A --es voltcap_test_inspect "$N||0|0"; sleep 5
input keyevent 106; sleep 3; input keyevent 106; sleep 4
screencap -p "$OUT/${f}_a0.png"
input swipe 330 290 255 290 450; sleep 4; screencap -p "$OUT/${f}_a1.png"
input swipe 330 290 255 290 450; sleep 4; screencap -p "$OUT/${f}_a2.png"
input swipe 330 290 180 290 450; sleep 4; screencap -p "$OUT/${f}_a3.png"
input swipe 330 290 180 290 450; sleep 4; screencap -p "$OUT/${f}_a4.png"
input keyevent 97; sleep 1; input keyevent 97; sleep 1
