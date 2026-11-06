# v0.6.66 update 444 popup polish check (preview only; launches blocked by voltcap_test_nolaunch). Per layout x style:
# home still, open recording + stills (+1.2 s settled, +5 s rested), D-pad walk stills, close recording, after-B still
# (same game as before?), then rapid A B A B and A B B (120 ms) — any window left open is closed and noted.
# usage: sh popfinal.sh TAG "LAYOUT:STYLE ..." [size] [game]   (game: part of a name, focused first via voltcap_test_game)
# Never `gfxinfo reset` here (D139).
P=com.shelf.launcher; TAG=$1; COMBOS=$2; SZ=${3:-960x540}; GAME=$4
V=/data/local/tmp/vqa; OUT=/sdcard/pf/$TAG; rm -rf $OUT; mkdir -p $OUT
# (D136: the "Who's playing?" profile picker can come up in front after a while — B keeps the current profile, nothing else.)
# (D136, update 452: the app's own screen mark first — uiautomator can't dump an animated home on the RP Classic, so the
# picker was missed and the moves walked it; the dump stays as the fallback for builds without the mark.)
picker() { m=/sdcard/Android/data/com.shelf.launcher/files/voltcap_qa_screen; if [ -s $m ] && grep -q "^PICKER $(pidof com.shelf.launcher | cut -d' ' -f1)" $m; then input keyevent KEYCODE_BUTTON_B; sleep 1.5; echo "picker dismissed (mark)"; elif [ ! -s $m ]; then uiautomator dump /sdcard/vqa_ui.xml > /dev/null 2>&1; grep -q "Who.s playing" /sdcard/vqa_ui.xml 2>/dev/null && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; echo "picker dismissed"; }; rm -f /sdcard/vqa_ui.xml; fi; }
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
K() { sh $V/vqa.sh seq "$1" ${2:-150} > /dev/null; }
# The focused window's token: the game window is a window of its own (same activity name, another token).
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" ; }
logcat -c
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 3
for c in $COMBOS; do
  lay=${c%%:*}; st=${c##*:}; n=${lay}_$st
  # (The game first — it switches to its console's category, which may have a layout of its own — then the layout asked for.)
  [ -n "$GAME" ] && { A --es voltcap_test_game "$GAME"; sleep 3; }
  A --es voltcap_test_layout $lay; sleep 4
  A --es voltcap_test_pref "popup_style_by_layout=$lay=$st"; sleep 2
  picker >> $OUT/log.txt
  screencap -p $OUT/${n}_0home.png
  H=$(W)
  (screenrecord --time-limit 3 --size $SZ --bit-rate 10000000 $OUT/${n}_open.mp4 > /dev/null 2>&1 &); sleep 0.5
  K "A" 100; sleep 1.2
  # (A first press after a layout switch can land before the home screen takes keys: once more, and noted.)
  # (Row opens up / Row reveal draw in the home screen's own window: no token change there, so no second A — it would press Play.)
  case $st in ROW_*) ;; *) [ "$(W)" = "$H" ] && { echo "$n: window not open after A, A again" >> $OUT/log.txt; K "A" 100; sleep 1.2; };; esac
  screencap -p $OUT/${n}_1settled.png
  sleep 3.4
  screencap -p $OUT/${n}_2rested.png
  K "R R" 400; screencap -p $OUT/${n}_3right2.png
  K "D" 400; screencap -p $OUT/${n}_4down.png
  K "U L L" 400; screencap -p $OUT/${n}_5back.png
  (screenrecord --time-limit 2 --size $SZ --bit-rate 10000000 $OUT/${n}_close.mp4 > /dev/null 2>&1 &); sleep 0.5
  K "B" 100; sleep 2
  screencap -p $OUT/${n}_6afterB.png
  case $st in ROW_*) ;; *) [ "$(W)" = "$H" ] || echo "$n: NOT back on the home window after B" >> $OUT/log.txt;; esac
  K "A B A B" 120; sleep 2.2
  screencap -p $OUT/${n}_7rapid1.png
  K "A B B" 120; sleep 2.2
  screencap -p $OUT/${n}_8rapid2.png
  case $st in ROW_*) ;; *) [ "$(W)" = "$H" ] || { echo "$n: window left open after rapid A/B - B" >> $OUT/log.txt; K "B" 100; sleep 1.5; };; esac
  echo "$n done" >> $OUT/log.txt
done
logcat -d -s VoltcapQA | grep -E "LAUNCH|win close|glance|reveal" > $OUT/qa.txt
logcat -d | grep -E "FATAL|AndroidRuntime" > $OUT/crash.txt
# (489c, D287: the launch block stays on — only the release build's clean-up removes it)
echo done > $OUT/DONE
