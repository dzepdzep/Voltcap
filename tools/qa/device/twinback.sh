# v0.6.67 (update 447): the way back from the second screen's dashboard on the five two-screen layouts (AYN Thor).
# usage: sh twinback.sh TAG ["LAYOUTS"] [TAPX TAPY]   — layouts are previews (voltcap_test_preview: nothing saved); the dashboard is
# opened with voltcap_test_twindash (as the layout's own button does). Per layout: top + bottom stills of the layout, the dashboard,
# the next page; with TAPX/TAPY (lower-screen px) a tap there, then a still to prove the layout came back. Launches blocked.
P=com.shelf.launcher; TAG=$1; LAYOUTS=${2:-"TWIN_DESK TWIN_ICONS TWIN_SHOWCASE POCKET_CLASSIC POCKET_RIBBON"}
TX=$3; TY=$4
D0=4630946441858561667; D1=4630946482288158084
OUT=/sdcard/pp/$TAG; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
S() { screencap -d $D0 -p $OUT/${1}_top.png; screencap -d $D1 -p $OUT/${1}_bot.png; }
A --ez voltcap_test_nolaunch true; sleep 1
A --ez voltcap_test_preview true; sleep 1
for L in $LAYOUTS; do
  A --es voltcap_test_twindash off; sleep 0.5
  A --es voltcap_test_layout $L; sleep 3.5
  S ${L}_1layout
  A --es voltcap_test_twindash on; sleep 2.5
  S ${L}_2dash
  input -d 4 swipe 900 600 300 600 250; sleep 1.5
  screencap -d $D1 -p $OUT/${L}_3page_bot.png
  if [ -n "$TX" ]; then input -d 4 tap $TX $TY; sleep 2; screencap -d $D1 -p $OUT/${L}_4back_bot.png; fi
  echo "$L done" >> $OUT/log.txt
done
A --es voltcap_test_twindash off; sleep 0.5
A --ez voltcap_test_preview false; sleep 1.5
logcat -d | grep -E "FATAL|AndroidRuntime: " | grep -i shelf > $OUT/crash.txt
logcat -d | grep -E "VoltcapQA.*LAUNCH|blocked launch" | tail -5 > $OUT/launch.txt
echo done > $OUT/DONE
