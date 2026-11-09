#!/system/bin/sh
# Update 511: rapid A-B-A-B (0.12 s) N times on a layout in the QA app; C = popup closed after it, O = still open (then closed with B).
P=com.shelf.launcher.qa; L=$1; N=${2:-10}
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" | tail -1; }
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout $L --es voltcap_test_game "Mario Kart 64" >/dev/null 2>&1; sleep 3.5; H=$(W); r=""
for i in $(seq 1 $N); do
  input keyevent 96; sleep 0.12; input keyevent 97; sleep 0.12; input keyevent 96; sleep 0.12; input keyevent 97; sleep 2.0
  if [ "$(W)" = "$H" ]; then r="${r}C"; else r="${r}O"; screencap -p /sdcard/Download/rap_${L}_$i.png; input keyevent 97; sleep 1.2; fi
done
echo "rapid $L $r"
