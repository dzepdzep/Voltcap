# Update 475 (D249 triage): is the first D-pad press after arriving on the home screen lost — and after WHAT arrival?
# Per trial: arrive (hook = voltcap_test_game intent, plain = am start with no extras, home = the HOME key when Voltcap is
# the launcher), wait, dump the app's focus, ONE DPAD_RIGHT, dump again: moved=1 / 0. Then a second press: moved2.
# Preview only, launches blocked, on the layout given. setsid sh d249.sh TAG LAYOUT "GAME" ROUNDS
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; TAG=$1; LAY=$2; GAME=$3; N=${4:-4}
OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT; rm -f $F/qa_clip/${TAG}_*
. /data/local/tmp/evidence.sh
ev_start $OUT $TAG "kind=d249" "layouts=$LAY" "game=$GAME" "content=default"
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
dump() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; i=0; while [ ! -s $F/qa_clip/${TAG}_$1.json ] && [ $i -lt 20 ]; do sleep 0.15; i=$((i+1)); done; }
foc() { grep -o '"focusedGame":"[^"]*"' $F/qa_clip/${TAG}_$1.json 2>/dev/null | head -1; }
input keyevent KEYCODE_WAKEUP; sleep 0.5; touch $F/qa_nolaunch
# (Arrival 0 — "fresh": the app restarted (force-stop, start), the walk's own first state; then the rounds.)
[ "$FRESH" = 1 ] && { am force-stop $P; sleep 1; A; sleep 6; }
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; A --es voltcap_test_layout $LAY; sleep 3.5
r=0
while [ $r -lt $N ]; do
  r=$((r+1))
  for kind in hook plain home; do
    dumpsys power | grep -q "mWakefulness=Awake" || input keyevent KEYCODE_WAKEUP
    A --es voltcap_test_game "$GAME"; sleep 2.5
    case $kind in
      hook) A --es voltcap_test_game "$GAME";;
      plain) A;;
      home) input keyevent KEYCODE_HOME;;
    esac
    sleep 2.5
    fg=$(dumpsys window | grep -m1 mCurrentFocus)
    case "$fg" in *com.shelf.launcher*) ;; *) echo "$r $kind not-in-front" >> $OUT/trials.txt; A; sleep 2; continue;; esac
    screencap -p $OUT/${r}_${kind}_a.png; dump ${r}_${kind}_a; input keyevent KEYCODE_DPAD_RIGHT; sleep 1.2; screencap -p $OUT/${r}_${kind}_b.png; dump ${r}_${kind}_b
    echo "$r $kind screen=$(cat $F/voltcap_qa_screen 2>/dev/null) pid=$(pidof $P)" >> $OUT/marks.txt
    input keyevent KEYCODE_DPAD_RIGHT; sleep 1.2; dump ${r}_${kind}_c
    a=$(foc ${r}_${kind}_a); b=$(foc ${r}_${kind}_b); c=$(foc ${r}_${kind}_c)
    [ "$a" != "$b" ] && m1=1 || m1=0; [ "$b" != "$c" ] && m2=1 || m2=0
    echo "$r $kind moved=$m1 moved2=$m2" >> $OUT/trials.txt
  done
done
A --ez voltcap_test_nolaunch false; A --ez voltcap_test_preview false; rm -f $F/qa_nolaunch
mkdir -p $OUT/qa_clip; cp $F/qa_clip/${TAG}_* $OUT/qa_clip/ 2>/dev/null
ev_end $OUT
echo DONE >> $OUT/log.txt
