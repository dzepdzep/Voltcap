# Update 475: D248 / D250 checks after a fresh start. Per round: force-stop + start Voltcap (as after an install or a reboot),
# close "Who's playing?" (B, by the app's own mark), then (1) Voltcap row on GAME: the title's place on arrival and after
# one move away and back (D248: same place every time), (2) Cinema on GAME: one RIGHT — did the focus move (D250)?
# Dumps + screenshots per step; preview only, launches blocked.   setsid sh firstvisit.sh TAG "GAME" ROUNDS
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; TAG=$1; GAME=$2; N=${3:-3}
OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT; rm -f $F/qa_clip/${TAG}_*
. /data/local/tmp/evidence.sh
ev_start $OUT $TAG "kind=firstvisit" "layouts=CLASSIC CINEMA" "game=$GAME" "content=default"
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; }
dump() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; i=0; while [ ! -s $F/qa_clip/${TAG}_$1.json ] && [ $i -lt 20 ]; do sleep 0.15; i=$((i+1)); done; screencap -p $OUT/$1.png; }
foc() { grep -o '"focusedGame":"[^"]*"' $F/qa_clip/${TAG}_$1.json 2>/dev/null | head -1; }
r=0
while [ $r -lt $N ]; do
  r=$((r+1))
  k WAKEUP; am force-stop $P; sleep 1; A; sleep 6
  pid=$(pidof $P); for i in 1 2 3 4 5 6 7 8; do grep -q " $pid\$" $F/voltcap_qa_screen 2>/dev/null && break; sleep 0.8; done
  for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B; sleep 1.5; echo "$r picker closed" >> $OUT/log.txt; done
  touch $F/qa_nolaunch; A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true
  A --es voltcap_test_layout CLASSIC; sleep 3.5; A --es voltcap_test_game "$GAME"; sleep 2.5
  dump ${r}_classic_a; k DPAD_RIGHT; sleep 1.4; k DPAD_LEFT; sleep 1.4; dump ${r}_classic_b
  A --es voltcap_test_layout CINEMA; sleep 3.5; A --es voltcap_test_game "$GAME"; sleep 2.5
  dump ${r}_cinema_a; k DPAD_RIGHT; sleep 1.4; dump ${r}_cinema_b; k DPAD_RIGHT; sleep 1.4; dump ${r}_cinema_c
  echo "$r mark=$(cut -d' ' -f1 $F/voltcap_qa_screen) cinema a=$(foc ${r}_cinema_a | tail -c 30) b=$(foc ${r}_cinema_b | tail -c 30) c=$(foc ${r}_cinema_c | tail -c 30)" >> $OUT/trials.txt
done
A --ez voltcap_test_nolaunch false; A --ez voltcap_test_preview false; rm -f $F/qa_nolaunch
mkdir -p $OUT/qa_clip; cp $F/qa_clip/${TAG}_* $OUT/qa_clip/ 2>/dev/null
ev_end $OUT
echo DONE >> $OUT/log.txt
