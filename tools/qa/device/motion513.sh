#!/system/bin/sh
# Update 513: motion audit — every home layout in the isolated QA app: a short screen recording of game-to-game moves
# (2 steps right, 1 left, a burst of 3), the window opening (A) and closing (B), for tools/qa/tools/flash513.py (one-frame
# flashes) and frame review. Keys only while the QA app has the focus. sh motion513.sh TAG [LAYOUTS]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=/data/local/tmp/m513_$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
LAYS=${2:-"CLASSIC TILES HERO GRID TV DASH LIVE SIDE CROSS SPOT BAR SHELF HUB RESUME CHAN CIRCUIT LIBRARY STACKS TIMELINE ARCADE CONSOLES DISCS HERO_TILES TWIN_DESK TWIN_ICONS TWIN_SHOWCASE CRATE CARDS CUSTOM POCKET_CLASSIC POCKET_RIBBON GREENCORE POCKET CUBE MEMCARD CASEFLOW BOX STORAGE FILESELECT ORBIT AIRY NIGHT BLADES BUBBLES CAMERA PANELS MAGAZINE SHOP TERMINAL CINEMA ARCTIC COVERDETAILS ARTLIB"}
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
k() { f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) ;; *) echo "ABORT focus $f" >> $L; exit 2;; esac
  input keyevent $1; sleep ${2:-0.6}; }
W=$(wm size | grep -o '[0-9]*x[0-9]*' | tail -1); SW=$(( ${W%x*} / 2 )); SH=$(( ${W#*x} / 2 ))
A --ez voltcap_test_nolaunch true; A --ez voltcap_test_preview true; sleep 2
for LY in $LAYS; do
  A --es voltcap_test_layout $LY --es voltcap_test_game "Mario Kart 64"; sleep 3.5
  grep -q "^PICKER $(pidof $P)" $F/voltcap_qa_screen 2>/dev/null && k 97 1.5
  screenrecord --time-limit 7 --bit-rate 6000000 $O/$LY.mp4 & sleep 0.8
  k 22 0.7; k 22 0.7; k 21 0.7; k 22 0.1; k 22 0.1; k 22 1.0; k 96 1.5; k 97 1.0
  wait; echo "$LY done" >> $L
done
A --ez voltcap_test_preview false; echo DONE >> $L
