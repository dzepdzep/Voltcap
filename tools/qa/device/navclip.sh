# Update 474 — home-screen navigation evidence (QA tier T5): per layout, the game you're on is moved RIGHT, LEFT, LEFT, RIGHT (475)
# (DOWN / UP where RIGHT doesn't move it), and after every key: an early raw screenshot (~0.15 s + capture, no PNG encoding) and, once settled (1.4 s), a
# screenshot + QaClip dump. tools/qa/tools/navclip.py then checks: the same game comes back with its title / art in the same
# place (jumping), its picture is the same as before (stale / wrong art) and differs from the neighbour's, the picture was
# there early or how late it came, the open window's game, and Coil's own load times (QaArt).
# Run detached:  setsid sh navclip.sh TAG "LAYOUTS" "GAME" &   (wrapped in voltcap_test_snapshot save / restore by the caller)
# Preview only (nothing saved), launches blocked, owner's view state untouched by the caller's snapshot. Needs evidence.sh.
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; LAYS=$2; GAME=$3
OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT; rm -f $F/qa_clip/${TAG}_*
. "$(dirname "$0")/evidence.sh" 2>/dev/null || . /data/local/tmp/evidence.sh
ev_start $OUT $TAG "kind=navclip" "layouts=$LAYS" "game=$GAME" "content=${QA_CONTENT:-default}" "category=${QA_CATEGORY:-}" "script_sha256=$(sha256sum "$0" | cut -d' ' -f1)"
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; }
dump() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; i=0; while [ ! -s $F/qa_clip/${TAG}_$1.json ] && [ $i -lt 20 ]; do sleep 0.15; i=$((i+1)); done; }
foc() { grep -o '"focusedGame":"[^"]*"' $F/qa_clip/${TAG}_$1.json 2>/dev/null | head -1; }
input keyevent KEYCODE_WAKEUP; sleep 0.5
pid=$(pidof $P); for i in 1 2 3 4 5 6 7 8; do grep -q " $pid\$" $F/voltcap_qa_screen 2>/dev/null && break; sleep 0.8; done
for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B; sleep 1.5; echo "profile picker closed with B" >> $OUT/log.txt; done
touch $F/qa_nolaunch; A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 1
for lay in $LAYS; do
  dumpsys power | grep -q "mWakefulness=Awake" || k WAKEUP
  A --ez voltcap_test_preview true; A --es voltcap_test_layout $lay; sleep 3.5
  # (QA_CATEGORY=All: the walk runs in that category — the Odin's consoles hold one or two games each, a walk there can't move.)
  [ -n "$QA_CATEGORY" ] && { A --es voltcap_test_category "$QA_CATEGORY"; sleep 2; }
  [ -n "$GAME" ] && { A --es voltcap_test_game "$GAME"; sleep 2.5; }
  # (Update 475, D249: "Who's playing?" can come up again (a restart, the first start after an install): every key of the
  # walk then went to the picker and the layout looked stuck. Closed with B whenever the app's own mark says PICKER;
  # the mark at the start is logged, and navclip.py grades a walk that began on the picker INFRA.)
  for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B; sleep 1.5; echo "$lay picker closed with B" >> $OUT/log.txt; done
  echo "$lay mark=$(cut -d' ' -f1 $F/voltcap_qa_screen 2>/dev/null)" >> $OUT/steps.txt
  # (Update 475, D252: after a touch (popclip's taps, a finger) Android is in touch mode and swallows the next D-pad press
  # to leave it; some layouts also take focus only on the first key after a switch. The walk measures moving between
  # games, so it starts from settled controller focus: one ◀ and one ▶ first (NAV_PRELUDE=0 turns it off). The first-press
  # behaviour itself is checked by device/touchkey.sh and device/firstvisit.sh.)
  # (Update 477: QA_NOPRELUDE="TWIN_DESK ..." — layouts where ◀ ▶ leave the game list for another pane (Twin desk's toolbar,
  # k477t): the prelude would start the walk outside the games.)
  pre=${NAV_PRELUDE:-1}; case " $QA_NOPRELUDE " in *" $lay "*) pre=0;; esac
  [ "$pre" = 1 ] && { k DPAD_LEFT; sleep 0.8; k DPAD_RIGHT; sleep 1.4; echo "$lay prelude LEFT RIGHT" >> $OUT/steps.txt; }
  # (Update 477, T5 triage: QA_REC=1 records the walk — $OUT/${lay}.mp4, 12 s — for tools/frames.py: whether a moved card or
  # text jumps, slides on purpose or was caught mid-animation by the settled screenshot.)
  [ -n "$QA_REC" ] && { rm -f $OUT/${lay}.mp4; (screenrecord --bit-rate 20000000 --time-limit 12 $OUT/${lay}.mp4 > /dev/null 2>&1 &); sleep 0.8; }
  s=0; screencap -p $OUT/${lay}_s0.png; dump ${lay}_s0
  # (Update 475: QA_VERTICAL="CROSS ..." — layouts whose games run up and down (◀ ▶ change something else: Cross's category
  # columns) are walked with ▼ ▲ from the start. The walk is R L L R: back on the first game at s2 and s4, a row's end
  # either side shows as a key that changed nothing next to one that did.)
  axis=RIGHT; back=LEFT
  case " $QA_VERTICAL " in *" $lay "*) axis=DOWN; back=UP; echo "$lay walked DOWN / UP (QA_VERTICAL)" >> $OUT/log.txt;; esac
  for key in A1 B1 B2 A2; do
    s=$((s+1))
    case $key in A*) kk=DPAD_$axis;; *) kk=DPAD_$back;; esac
    t0=$(date +%s%N 2>/dev/null | cut -c1-13); k $kk
    sleep 0.15; screencap $OUT/${lay}_s${s}e.raw; t1=$(date +%s%N 2>/dev/null | cut -c1-13)
    sleep 1.25; screencap -p $OUT/${lay}_s${s}.png; dump ${lay}_s${s}
    echo "$lay s$s key=$kk early_ms=$((t1 - t0))" >> $OUT/steps.txt
    # (The first move that changes nothing: this layout moves the other way — DOWN / UP — and the walk starts again.)
    if [ $s -eq 1 ] && [ "$(foc ${lay}_s1)" = "$(foc ${lay}_s0)" ] && [ $axis = RIGHT ] && [ "$NO_SWITCH" != 1 ]; then
      axis=DOWN; back=UP; echo "$lay RIGHT moved nothing: DOWN / UP" >> $OUT/log.txt
      s=1; k DPAD_DOWN; sleep 0.15; screencap $OUT/${lay}_s1e.raw; sleep 1.25; screencap -p $OUT/${lay}_s1.png; dump ${lay}_s1
      echo "$lay s1 key=DPAD_DOWN early_ms=150" >> $OUT/steps.txt
    fi
  done
  [ -n "$QA_REC" ] && { i=0; while pidof screenrecord > /dev/null && [ $i -lt 30 ]; do sleep 0.5; i=$((i+1)); done; }
  fg=$(dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity")
  case "$fg" in *com.shelf.launcher*) ;; *) echo "STOP: not Voltcap in front after $lay" >> $OUT/log.txt; break;; esac
  echo "$lay done" >> $OUT/log.txt
done
A --ez voltcap_test_preview false
mkdir -p $OUT/qa_clip; cp $F/qa_clip/${TAG}_* $OUT/qa_clip/ 2>/dev/null
ev_end $OUT
echo DONE >> $OUT/log.txt
