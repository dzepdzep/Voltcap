# Update 447 (D144): picker447.sh (the new picker.sh) on every "Who's playing?" look — sample profiles (ids 900+, nothing saved).
# usage: sh pickdetect.sh TAG   → /sdcard/pp/TAG/result.txt lines "LOOK picker=X home=Y"; PASS only if PICKER / HOME each time.
P=com.shelf.launcher; OUT=/sdcard/pp/$1; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
ok=1
for L in AURORA CONSOLE NEON OLED THEME MIDNIGHT; do
  A --es voltcap_test_pickerlook $L; sleep 1
  A --es voltcap_test_profiles "3:active=1"; sleep 2.5
  a=$(sh /data/local/tmp/vqa/picker447.sh)
  input keyevent KEYCODE_BUTTON_B; sleep 1.8
  b=$(sh /data/local/tmp/vqa/picker447.sh)
  echo "$L picker=$a home=$b" >> $OUT/result.txt
  [ "$a" = PICKER ] && [ "$b" = HOME ] || ok=0
done
A --es voltcap_test_pickerlook off; A --es voltcap_test_profiles off
[ $ok = 1 ] && echo PASS >> $OUT/result.txt || echo FAIL >> $OUT/result.txt
