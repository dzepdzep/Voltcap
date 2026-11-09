#!/system/bin/sh
# Update 515: the Xbox 360 / Xbox One / PS3 / PS4 / PS5 cases as their OWN games get them — isolated Voltcap QA app ONLY.
# Five empty stand-in files in the QA app's own roms folder (folders PS3 / PS4 / Xbox360 / PS5 / XboxOne — the scanner keeps
# an unknown folder's name as the console), the QA fixture's portrait art as every cover, each held in Inspect at the front,
# 45°, the spine, the back, from above, and opened. The stand-ins are removed and the QA app restarted at the end (rescan).
#   sh xcase515b.sh TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt; R=$F/roms
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
focus() { dumpsys window | grep mCurrentFocus | tail -1; }
k() { case "$(focus)" in *com.shelf.launcher.qa*) input keyevent $1; sleep ${2:-0.8};; *) echo "ABORT $(focus)" >> $L; echo DONE >> $L; exit 2;; esac; }
echo "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher)" >> $L
A --ez voltcap_test_nolaunch true; sleep 3; A --es voltcap_test_snapshot save; sleep 2; k 97 1
for C in PS3 PS4 Xbox360 PS5 XboxOne; do mkdir -p $R/$C; echo qa > "$R/$C/QA Case $C.iso"; done
am start -a android.settings.SETTINGS >/dev/null 2>&1; sleep 1; am force-stop $P; sleep 1
A --ez voltcap_test_nolaunch true; sleep 8; k 97 1
A --ez voltcap_test_preview true; sleep 1
A --es voltcap_test_fixture "names=real,art=portrait,meta=none"; sleep 3
for C in PS3 PS4 Xbox360 PS5 XboxOne; do
  for pose in "0|0" "45|6" "90|0" "180|0" "25|40"; do
    k 97 0.6; A --es voltcap_test_inspect "QA_Case_$C||${pose%|*}|${pose#*|}"; sleep 3.2
    screencap -p "$O/${C}_$(echo $pose | tr '|' '_').png"
  done
  A --es voltcap_test_pref "inspect_open_at_start=true"; sleep 0.8
  k 97 0.6; A --es voltcap_test_inspect "QA_Case_$C||20|4"; sleep 4.5; screencap -p "$O/${C}_open.png"
  A --es voltcap_test_pref "inspect_open_at_start=false"; sleep 0.8
  echo "case $C" >> $L
done
k 97 0.8; A --es voltcap_test_fixture off; sleep 1; A --ez voltcap_test_preview false; sleep 0.5
for C in PS3 PS4 Xbox360 PS5 XboxOne; do rm -f "$R/$C/QA Case $C.iso"; rmdir $R/$C; done
am start -a android.settings.SETTINGS >/dev/null 2>&1; sleep 1; am force-stop $P; sleep 1
A --ez voltcap_test_nolaunch true; sleep 8; k 97 1
A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cat $F/voltcap_test_snapshot_verify.txt 2>/dev/null)" >> $L; echo "roms: $(ls $R | tr '\n' ' ')" >> $L
echo "launches=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH') real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L
