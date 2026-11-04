# Update 477 (D266): Airy's More-open keys reached with the controller only. Opens the window with A, walks RIGHT to More, A,
# then DOWN / RIGHT through the pill row; a dump after each key records which control has focus. setsid sh airykeys.sh TAG GAME
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; TAG=$1; GAME=$2
OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT; rm -f $F/qa_clip/${TAG}_*
. /data/local/tmp/evidence.sh
ev_start $OUT $TAG "kind=airykeys" "layouts=AIRY" "game=$GAME" "content=default"
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-1.0}; }
dump() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; i=0; while [ ! -s $F/qa_clip/${TAG}_$1.json ] && [ $i -lt 20 ]; do sleep 0.15; i=$((i+1)); done; screencap -p $OUT/$1.png; }
foc() { grep -o '"label":"[^"]*","kind":"control","full":\[[0-9,-]*\],"shown":\[[0-9,-]*\],"focused":true' $F/qa_clip/${TAG}_$1.json 2>/dev/null | head -1 | sed 's/"label":"//; s/",.*//'; }
k WAKEUP 0.5; touch $F/qa_nolaunch; A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true
A --es voltcap_test_layout AIRY; sleep 3.5
for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 1.5; done
A --es voltcap_test_game "$GAME"; sleep 2
k BUTTON_A 2.0; dump 0_open; echo "0 open focus=$(foc 0_open)" >> $OUT/walk.txt
n=0
for key in DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT BUTTON_A DPAD_DOWN DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_UP; do
  n=$((n+1)); k $key; dump ${n}_$key; echo "$n $key focus=$(foc ${n}_$key)" >> $OUT/walk.txt
done
k BUTTON_B 1.3; k BUTTON_B 1.3
A --ez voltcap_test_nolaunch false; A --ez voltcap_test_preview false; rm -f $F/qa_nolaunch
mkdir -p $OUT/qa_clip; cp $F/qa_clip/${TAG}_* $OUT/qa_clip/ 2>/dev/null
ev_end $OUT; echo DONE >> $OUT/log.txt
