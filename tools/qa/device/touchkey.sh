# Update 475 (D252 triage): after a touch, is the first D-pad press lost? Per round on LAYOUT (game GAME, preview, launches
# blocked): (a) arrive and press RIGHT with no touch before; (b) tap the screen's top-left corner (the status area — nothing
# there opens anything), then press RIGHT. Focus before / after from the app's own dump. setsid sh touchkey.sh TAG LAYOUT GAME N
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; TAG=$1; LAY=$2; GAME=$3; N=${4:-3}
OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT; rm -f $F/qa_clip/${TAG}_*
. /data/local/tmp/evidence.sh
ev_start $OUT $TAG "kind=touchkey" "layouts=$LAY" "game=$GAME" "content=default"
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
dump() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; i=0; while [ ! -s $F/qa_clip/${TAG}_$1.json ] && [ $i -lt 20 ]; do sleep 0.15; i=$((i+1)); done; }
foc() { grep -o '"focusedGame":"[^"]*"' $F/qa_clip/${TAG}_$1.json 2>/dev/null | head -1 | tail -c 32; }
input keyevent KEYCODE_WAKEUP; touch $F/qa_nolaunch; A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true
A --es voltcap_test_layout $LAY; sleep 3.5
r=0
while [ $r -lt $N ]; do
  r=$((r+1))
  for kind in key touch; do
    A --es voltcap_test_game "$GAME"; sleep 2.5
    for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; input keyevent KEYCODE_BUTTON_B; sleep 1.5; done
    input keyevent KEYCODE_DPAD_LEFT; sleep 1.2; input keyevent KEYCODE_DPAD_RIGHT; sleep 1.2   # (keys on: not in touch mode)
    [ $kind = touch ] && { input tap 5 5; sleep 1.2; }
    dump ${r}_${kind}_a; screencap -p $OUT/${r}_${kind}_a.png
    input keyevent KEYCODE_DPAD_RIGHT; sleep 1.4; dump ${r}_${kind}_b; screencap -p $OUT/${r}_${kind}_b.png
    echo "$r $kind before=$(foc ${r}_${kind}_a) after=$(foc ${r}_${kind}_b)" >> $OUT/trials.txt
  done
done
A --ez voltcap_test_preview false
mkdir -p $OUT/qa_clip; cp $F/qa_clip/${TAG}_* $OUT/qa_clip/ 2>/dev/null
ev_end $OUT; echo DONE >> $OUT/log.txt
