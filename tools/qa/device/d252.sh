# Update 477 (D252 triage): after a touch, is the first D-pad press lost — and by which layer? The app (QA build) logs each
# key that reaches the activity ("key … touchMode=… focus=…", "keydone … handled=") and every touch-mode change; the home's
# focused game comes from its own dump. Per round on LAYOUT: (a) keys only; (b) a tap on the status area then RIGHT, RIGHT;
# (c) a tap then A; (d) a tap then UP. setsid sh d252.sh TAG LAYOUT GAME N
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; TAG=$1; LAY=$2; GAME=$3; N=${4:-3}
OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT; rm -f $F/qa_clip/${TAG}_*
. /data/local/tmp/evidence.sh
ev_start $OUT $TAG "kind=d252" "layouts=$LAY" "game=$GAME" "content=default"
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-1.2}; }
dump() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; i=0; while [ ! -s $F/qa_clip/${TAG}_$1.json ] && [ $i -lt 20 ]; do sleep 0.15; i=$((i+1)); done; }
foc() { grep -o '"focusedGame":"[^"]*"' $F/qa_clip/${TAG}_$1.json 2>/dev/null | head -1 | sed 's/.*\///; s/"$//'; }
pop() { grep -o '"popupGame":"[^"]*"' $F/qa_clip/${TAG}_$1.json 2>/dev/null | head -1 | sed 's/.*\///; s/"$//'; }
k WAKEUP 0.5; touch $F/qa_nolaunch; A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true
A --es voltcap_test_layout $LAY; sleep 3.5
for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 1.5; done
r=0
while [ $r -lt $N ]; do
  r=$((r+1))
  for kind in keys tapright tapA tapup; do
    A --es voltcap_test_game "$GAME"; sleep 2
    k DPAD_LEFT; k DPAD_RIGHT          # (keys on: not in touch mode)
    case $kind in keys) ;; *) input tap 5 5; sleep 1.2;; esac
    dump ${r}_${kind}_0; logcat -c
    case $kind in
      keys|tapright) k DPAD_RIGHT 1.3; dump ${r}_${kind}_1; k DPAD_RIGHT 1.3; dump ${r}_${kind}_2;;
      tapA) k BUTTON_A 1.8; dump ${r}_${kind}_1; k BUTTON_B 1.3;;
      tapup) k DPAD_UP 1.3; dump ${r}_${kind}_1; k DPAD_DOWN 1.3;;
    esac
    logcat -d -s VoltcapQA | grep -E ' (key|keydone|touchmode|leftouch) ' | sed 's/^.*VoltcapQA: //' > $OUT/${r}_${kind}_log.txt
    echo "$r $kind f0=$(foc ${r}_${kind}_0) f1=$(foc ${r}_${kind}_1) f2=$(foc ${r}_${kind}_2) pop1=$(pop ${r}_${kind}_1) keys_seen=$(grep -c ' key ' $OUT/${r}_${kind}_log.txt) tm=$(grep ' touchmode ' $OUT/${r}_${kind}_log.txt | head -2 | tr '\n' ';')" >> $OUT/trials.txt
  done
done
A --ez voltcap_test_nolaunch false; A --ez voltcap_test_preview false; rm -f $F/qa_nolaunch
mkdir -p $OUT/qa_clip; cp $F/qa_clip/${TAG}_* $OUT/qa_clip/ 2>/dev/null
ev_end $OUT; echo DONE >> $OUT/log.txt
