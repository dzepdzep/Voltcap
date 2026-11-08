#!/system/bin/sh
# Update 501 (isolated QA app only): cartridges / cases held at set angles (TestHooks.inspect: nothing saved, no game).
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; O=$F/cart501; mkdir -p $O; rm -f $O/*.png
for g in "$@"; do
  for y in 0 40 90 140 180 -60; do
    input keyevent 224; input keyevent 97; sleep 0.8
    am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_inspect "$g||$y|0" >/dev/null; sleep 3.2
    screencap -p $O/${g}_y$y.png
  done
done
input keyevent 97
logcat -d | grep -E "FATAL EXCEPTION|VoltcapTest: game" | tail -5 > $O/log.txt
