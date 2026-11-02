# v0.6.67 "Who's playing?" looks (preview only: sample profiles via voltcap_test_profiles — your real profiles are never
# switched, edited or removed; voltcap_test_pickerlook shows a look for this session, nothing saved).
# usage: sh pickerlooks.sh TAG ["LOOKS"]   per look: 3 samples (active=1), ▶ moved, 8 long names + a locked one, the editor (Y), the PIN
# pad, then your own profiles (real pictures) — B closes each. Stills in /sdcard/pp/TAG/.
P=com.shelf.launcher; TAG=$1; LOOKS=${2:-"AURORA CONSOLE NEON OLED THEME MIDNIGHT"}
OUT=/sdcard/pp/$TAG; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
K() { input keyevent $1; sleep ${2:-0.6}; }
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" ; }
H=$(W)
for L in $LOOKS; do
  A --es voltcap_test_pickerlook $L; sleep 1
  A --es voltcap_test_profiles "3:active=1"; sleep 2.5
  screencap -p $OUT/${L}_1three.png
  K KEYCODE_DPAD_RIGHT 0.8; screencap -p $OUT/${L}_2moved.png
  K KEYCODE_BUTTON_B 1.2
  A --es voltcap_test_profiles "8:long:locked"; sleep 2.5
  screencap -p $OUT/${L}_3eight.png
  K KEYCODE_DPAD_RIGHT 0.5; K KEYCODE_BUTTON_Y 1.2      # the locked sample asks its (sample) PIN before its editor
  screencap -p $OUT/${L}_4pin.png
  K KEYCODE_BUTTON_B 0.8; K KEYCODE_DPAD_LEFT 0.5; K KEYCODE_BUTTON_Y 1.2
  screencap -p $OUT/${L}_5edit.png
  K KEYCODE_BUTTON_B 1; K KEYCODE_BUTTON_B 1.2
  A --ez voltcap_who_is_playing true; sleep 2.5
  screencap -p $OUT/${L}_6yours.png
  K KEYCODE_BUTTON_B 1.5
  [ "$(W)" = "$H" ] || { echo "$L: something left open — B" >> $OUT/log.txt; K KEYCODE_BUTTON_B 1; }
  echo "$L done" >> $OUT/log.txt
done
A --es voltcap_test_pickerlook off
A --es voltcap_test_profiles off
logcat -d | grep -E "FATAL|AndroidRuntime: " | grep -v uiautomator > $OUT/crash.txt
echo done > $OUT/DONE
