# Update 490 (S1 / search / colour origin) — Settings with the group headers: open, D-pad through the categories, a touch
# swipe, the Theme page, close. Screenshots only; nothing is pressed that changes a setting (only ▲ ▼ ◀ ▶ and B, no A).
# sh settings490.sh TAG [DISPLAY] [NEXT PREV]  (sidebar: DPAD_DOWN DPAD_UP; compact chip row: DPAD_RIGHT DPAD_LEFT)  -> /sdcard/pc/TAG/*.png + log.txt
P=com.shelf.launcher; TAG=$1; D=${2:-0}; NX=KEYCODE_${3:-DPAD_DOWN}; PV=KEYCODE_${4:-DPAD_UP}; OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input -d $D keyevent $1; sleep ${2:-0.5}; }
shot() { screencap -d $D -p $OUT/$1.png 2>/dev/null || screencap -p $OUT/$1.png; echo "$1 $(date +%T)" >> $OUT/log.txt; }
input keyevent KEYCODE_WAKEUP
A --es voltcap_test_settings Home; sleep 2.5; shot 01_home
k $NX; k $NX; k $NX; k $NX; k $NX 1.2; shot 02_down5
k $NX; k $NX; k $NX 1.2; shot 03_down8
k $PV; k $PV; k $PV; k $PV; k $PV; k $PV; k $PV; k $PV 1.2; shot 04_backtop
A --es voltcap_test_settings Theme; sleep 2.5; shot 05_theme
A --es voltcap_test_settings "Updates_&_downloads"; sleep 2.5; shot 06_last
k KEYCODE_BUTTON_B 1.5; shot 07_closed
echo DONE >> $OUT/log.txt
