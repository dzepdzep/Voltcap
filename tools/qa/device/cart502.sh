#!/system/bin/sh
# Update 502 (isolated QA app only): the drawn Game Boy pak path Pokemon Brown uses (no label scan -> printed sticker on the
# drawn "gb" shell, cover from a made-up picture) held at many angles, incl. edge-on; then the 3D look's cartridge free-running.
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; O=$F/cart502; mkdir -p $O; rm -f $O/*.png
A(){ am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null; }
A --es voltcap_test_look CARTRIDGE_DISC; sleep 2
for y in 0 30 60 80 88 90 92 100 120 150 180 210 240 268 272 300 330; do
  input keyevent 224; input keyevent 97; sleep 0.7
  A --es voltcap_test_inspect "Pokemon_Brown|gb|$y|0|$F/qa_brown_cover.png|Pokemon Brown"; sleep 3
  screencap -p $O/brown_y$y.png
done
for p in -25 25; do input keyevent 97; sleep 0.7; A --es voltcap_test_inspect "Pokemon_Brown|gb|35|$p|$F/qa_brown_cover.png|Pokemon Brown"; sleep 3; screencap -p $O/brown_p$p.png; done
# free running (no held pose): idle wobble / sway, 8 frames 150 ms apart
input keyevent 97; sleep 0.7; A --es voltcap_test_game Pokemon_Brown; sleep 2.5; input keyevent 96; sleep 3.5
i=0; while [ $i -lt 8 ]; do screencap -p $O/brown_free_$i.png; sleep 0.15; i=$((i+1)); done
input keyevent 97; sleep 0.7
for c in "0,40,80,89,90,140,180" spin; do A --es voltcap_test_cards "$c"; sleep 3; screencap -p $O/cards_$c.png; done
i=0; while [ $i -lt 6 ]; do screencap -p $O/cards_spin_$i.png; sleep 0.3; i=$((i+1)); done
A --es voltcap_test_cards off
logcat -d | grep -E "FATAL EXCEPTION|VoltcapTest: game" | tail -5 > $O/log.txt
