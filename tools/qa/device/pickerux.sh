# v0.6.67 "Who's playing?" — D-pad and touch on sample profiles (ids 900+, never yours; nothing saved).
# usage: sh pickerux.sh TAG TAPX TAPY   (TAPX/TAPY = the 3rd sample's picture in px). Stills in /sdcard/pp/TAG/.
P=com.shelf.launcher; TAG=$1; OUT=/sdcard/pp/$TAG; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
K() { input keyevent $1; sleep ${2:-0.35}; }
A --es voltcap_test_pickerlook AURORA; sleep 1
# D-pad: ◀ on the first stays; ▶ to the end scrolls (fade on the left); ◀ back to the start.
A --es voltcap_test_profiles "8:long:locked"; sleep 2.5
K KEYCODE_DPAD_LEFT; K KEYCODE_DPAD_UP; screencap -p $OUT/1first.png
for i in 1 2 3 4 5 6 7 8 9 10; do K KEYCODE_DPAD_RIGHT; done; sleep 0.6; screencap -p $OUT/2end.png
for i in 1 2 3 4; do K KEYCODE_DPAD_LEFT; done; sleep 0.6; screencap -p $OUT/3middle.png
K KEYCODE_BUTTON_B 1.5
# Touch: tap the 3rd sample — the pick animation, then home (sample list cleared; your own profile stays active).
A --es voltcap_test_profiles "3:active=1"; sleep 2.5
input tap $2 $3; sleep 0.25; screencap -p $OUT/4tapanim.png; sleep 2; screencap -p $OUT/5after.png
A --ez voltcap_who_is_playing true; sleep 2.5; screencap -p $OUT/6yours.png; K KEYCODE_BUTTON_B 1.5
A --es voltcap_test_pickerlook off
A --es voltcap_test_profiles off
logcat -d | grep -E "FATAL|AndroidRuntime: " | grep -i shelf > $OUT/crash.txt
echo done > $OUT/DONE
