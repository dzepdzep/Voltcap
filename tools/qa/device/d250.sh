# Update 477 (D250 triage): Blades — what is selected when A is pressed, and which game the window opens. Per round: the run's
# game selected (voltcap_test_game), a probe dump + shot of the home, then KEYS (moves between blades) and A; the window's game
# from the app's own dump. Rounds also switch layout away and back. Preview, launches blocked. setsid sh d250.sh TAG GAME N
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; TAG=$1; GAME=$2; N=${3:-3}
OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT; rm -f $F/qa_clip/${TAG}_*
. /data/local/tmp/evidence.sh
ev_start $OUT $TAG "kind=d250" "layouts=BLADES" "game=$GAME" "content=default"
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-1.2}; }
dump() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; i=0; while [ ! -s $F/qa_clip/${TAG}_$1.json ] && [ $i -lt 20 ]; do sleep 0.15; i=$((i+1)); done; screencap -p $OUT/$1.png; }
fg() { grep -o "\"$2\":\"[^\"]*\"" $F/qa_clip/${TAG}_$1.json 2>/dev/null | head -1 | sed 's/.*\///; s/"$//'; }
hint() { grep -o '"label":"[^"]*"' $F/qa_clip/${TAG}_$1.json 2>/dev/null | grep -c 'Open its games'; }
k WAKEUP 0.5; touch $F/qa_nolaunch; A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true
r=0
while [ $r -lt $N ]; do
  r=$((r+1))
  A --es voltcap_test_layout CLASSIC; sleep 3; A --es voltcap_test_layout BLADES; sleep 3.5
  for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 1.5; done
  for seq in "none" "DPAD_RIGHT" "DPAD_RIGHT" "BUTTON_R1" "BUTTON_L1" "BUTTON_L1"; do
    A --es voltcap_test_game "$GAME"; sleep 2
    [ "$seq" != none ] && k $seq 1.5
    dump ${r}_${seq}_home
    k BUTTON_A 2.0; dump ${r}_${seq}_a1
    up=$(grep -c '"label":"Play"\|"label":"Continue"' $F/qa_clip/${TAG}_${r}_${seq}_a1.json 2>/dev/null)
    echo "$r key=$seq homeGame=$(fg ${r}_${seq}_home focusedGame) systemsHint=$(hint ${r}_${seq}_home) afterA_popup=$(fg ${r}_${seq}_a1 popupGame) afterA_focus=$(fg ${r}_${seq}_a1 focusedGame) playKeys=$up" >> $OUT/trials.txt
    k BUTTON_B 1.3; [ -n "$(fg ${r}_${seq}_a1 popupGame)" ] || true
  done
done
A --ez voltcap_test_preview false
mkdir -p $OUT/qa_clip; cp $F/qa_clip/${TAG}_* $OUT/qa_clip/ 2>/dev/null
ev_end $OUT; echo DONE >> $OUT/log.txt
