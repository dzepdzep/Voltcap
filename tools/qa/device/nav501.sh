#!/system/bin/sh
# Update 501 (isolated QA app only): focus after closing the game window / Settings, and after a fast D-pad burst.
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; O=$F/nav501; mkdir -p $O; rm -f $O/*.png
k(){ input keyevent $1; sleep ${2:-0.6}; }
s(){ screencap -p $O/$1.png; }
L=${1:-CLASSIC}
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout $L >/dev/null; sleep 3.5
k 22; k 22 1.2; s ${L}_0_before
k 96 2.5; s ${L}_1_window; k 97 2; s ${L}_2_after_window
k 108 2.5; s ${L}_3_settings; k 97 2; s ${L}_4_after_settings
i=0; while [ $i -lt 8 ]; do input keyevent 22; sleep 0.05; i=$((i+1)); done; sleep 0.25; s ${L}_5_burst_quick; sleep 1.5; s ${L}_6_burst_settled
logcat -d | grep -E "VoltcapQA: (LAUNCH|focus)|FATAL EXCEPTION" | tail -20 > $O/${L}_log.txt
