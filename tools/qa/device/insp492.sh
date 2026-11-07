# Update 492 (3D media QA) — a game's Inspect piece held at a list of turns (TestHooks voltcap_test_inspect, preview only:
# the game window opens with its piece out; nothing is launched, saved or changed). Screenshots to /sdcard/pc/TAG/.
# sh insp492.sh TAG "Game_name" "yaw yaw …" [PITCH] [SHELL] [DISPLAY]
P=com.shelf.launcher; TAG=$1; G=$2; YAWS=$3; PI=${4:-0}; SH=$5; D=${6:-0}; OUT=/sdcard/pc/$TAG; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
input keyevent KEYCODE_WAKEUP
for y in $YAWS; do
  A --es voltcap_test_inspect "$G|$SH|$y|$PI"; sleep 2.2
  if [ "$D" = 0 ]; then screencap -p $OUT/y${y}_p${PI}.png; else screencap -d $D -p $OUT/y${y}_p${PI}.png; fi
done
echo DONE >> $OUT/log.txt
