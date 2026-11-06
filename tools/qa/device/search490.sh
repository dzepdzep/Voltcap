# Update 490 (settings search QA) — types queries into Settings' own search box (QA hook) and screenshots the results; for
# jumping to a result is done by hand after checking where the focus is (A on a focused switch would change a setting).
# sh search490.sh TAG [DISPLAY] -> /sdcard/pc/TAG/*.png
P=com.shelf.launcher; TAG=$1; D=${2:-0}; OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
shot() { screencap -p $OUT/$1.png; echo "$1 $(date +%T)" >> $OUT/log.txt; }
input keyevent KEYCODE_WAKEUP
n=0
for q in spacing colour battery_watcher cube scrolling_animation; do
  n=$((n+1)); A --es voltcap_test_settings Home --es voltcap_test_settings_search "$q"; sleep 2.5; shot "q${n}_$q"
done
echo DONE >> $OUT/log.txt
