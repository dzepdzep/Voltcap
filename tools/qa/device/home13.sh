# home13.sh TAG — 523z2 targeted check (isolated QA app ONLY): the category name next to the status bar (Card binder, Cover
# details and the other layouts that show the shared Category name part) and File select, at text x1.0 and x1.3 / density
# x1.15 (voltcap_test_scale: Voltcap's own drawing only, no system setting). Per layout and size: the home screenshot + QaClip
# dump, then D-pad right x2 (shot + dump), left x2 back (shot + dump) - the controller still moves and nothing overlaps after
# moving. Preview only, launches blocked; QA settings snapshotted and restored. Results: /sdcard/pc/TAG, DONE at the end.
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT
. "$(dirname "$0")/evidence.sh" 2>/dev/null || . /data/local/tmp/evidence.sh
LAYS=${LAYS:-"CARDS COVERDETAILS FILESELECT HERO_TILES TWIN_ICONS TWIN_SHOWCASE TV HUB RESUME HERO SPOT BAR STACKS"}
ev_start $OUT $TAG "layouts=$LAYS" "sizes=x1.0,x1.3" "script_sha256=$(sha256sum "$0" | cut -d' ' -f1)"
. $(dirname $0)/qaguard.sh 2>/dev/null || . /data/local/tmp/qaguard.sh
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" > /dev/null 2>&1; }
# (A key only while a QA app window has the focus - as popclip.sh: a dialog / other window ends the run, nothing else is pressed.)
fg() { for w in 1 2 3 4 5 6; do f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *Application*) break;; *com.shelf.launcher.qa*) return 0;; *=null*) input keyevent KEYCODE_WAKEUP;; esac; sleep 0.5; done
  echo "ABORT focus=$f" >> $OUT/log.txt 2>/dev/null; am force-stop $P; exit 2; }
k() { fg; input keyevent KEYCODE_$1; sleep ${2:-0.7}; }
shot() { screencap -p $OUT/$1.png; echo "${TAG}_$1" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.1; }
A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5
A --es voltcap_test_snapshot save; sleep 2
A --es voltcap_test_artfix "delay=0"; sleep 4
touch $F/qa_nolaunch; qa_guard || { echo "STOP: launch guard not confirmed" >> $OUT/log.txt; echo DONE >> $OUT/log.txt; exit 1; }
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 1.5
# (A fresh QA app - just installed - opens on "Who's playing?": its mark is trusted only once it carries this process's pid, as in
# popclip.sh; then B keeps the profile. The first 523z3 run missed it and its x1.0 shots were all of the picker.)
pid=$(pidof $P); for i in 1 2 3 4 5 6 7 8 9 10; do grep -q " $pid\$" $F/voltcap_qa_screen 2>/dev/null && break; sleep 0.8; done
for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 1.5; echo "profile picker closed with B" >> $OUT/log.txt; done
for sz in 10 13; do
  if [ $sz = 13 ]; then A --es voltcap_test_scale font=1.3,density=1.15; else A --es voltcap_test_scale off; fi; sleep 3
  for lay in $LAYS; do
    dumpsys power | grep -q "mWakefulness=Awake" || k WAKEUP 1.5
    A --ez voltcap_test_preview true; A --es voltcap_test_layout $lay; sleep 3.5
    A --es voltcap_test_game "Super Mario World"; sleep 2.5
    grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_B 1.5; echo "picker closed before $lay" >> $OUT/log.txt; }
    n=x${sz}_$lay
    shot ${n}_a_home
    k DPAD_RIGHT 0.9; k DPAD_RIGHT 1.2; shot ${n}_b_right2
    k DPAD_LEFT 0.9; k DPAD_LEFT 1.2; shot ${n}_c_back
    echo "$n done" >> $OUT/log.txt
  done
done
A --es voltcap_test_scale off; sleep 1; A --es voltcap_test_snapshot restore; sleep 3; A --ez voltcap_test_preview false
rm -f $F/qa_nolaunch
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $OUT/log.txt
ev_end $OUT 2>/dev/null; echo DONE >> $OUT/log.txt
