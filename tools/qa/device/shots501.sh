#!/system/bin/sh
# Update 501: layout screenshots in the isolated QA app only (com.shelf.launcher.qa). Usage: shots501.sh <tag> LAYOUT...
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; O=$F/shots501; mkdir -p $O
T=$1; shift
for L in "$@"; do
  am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout $L >/dev/null; sleep 3.5
  screencap -p $O/${T}_${L}.png
  input keyevent 22; sleep 0.12; screencap -p $O/${T}_${L}_mid.png; sleep 1.2
  input keyevent 22; sleep 1.5; screencap -p $O/${T}_${L}_r2.png
done
