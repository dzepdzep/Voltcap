#!/system/bin/sh
# Update 513: physical-case depth audit — QA app only. Each fixture game held in Inspect at front, 3/4, side, back and from
# above (yaw|pitch), one screenshot each. sh case513.sh TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; O=$F/$1; rm -rf $O; mkdir -p $O
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
k() { f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) input keyevent $1; sleep ${2:-0.8};; *) echo "ABORT $f"; exit 2;; esac; }
for g in "Super_Mario_World" "Mario_Kart_64" "Super_Mario_Bros._3" "Sonic_the_Hedgehog_2" "Pokemon_-_Emerald_Version" "Mario_Kart_DS" "Crash_Bandicoot" "Shadow_of_the_Colossus" "Super_Mario_Sunshine"; do
  for pose in "0|0" "35|8" "90|0" "180|0" "25|40"; do
    k 97 0.6; A --es voltcap_test_inspect "$g||${pose%|*}|${pose#*|}"; sleep 2.6
    screencap -p "$O/${g}_$(echo $pose | tr '|' '_').png"
  done
done
k 97 0.8; echo DONE > $O/done.txt
