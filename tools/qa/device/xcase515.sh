#!/system/bin/sh
# Update 515: Xbox 360 / PS3 / PS4 cases (and the PS2 game's own, for reference) on the production renderer — isolated Voltcap QA app ONLY.
# The fixture game "Shadow of the Colossus" with the QA fixture's portrait art is put in each console's case through the R1 › Case
# pick (Features case_style, QA app's own setting, snapshotted first and restored + verified at the end), then held in Inspect
# at the front, 45°, the spine (90°), the back (180°), from above, and opened. One screenshot each.
#   sh xcase515.sh TAG     (opened: Inspect's own "open at start" switch, the QA app's setting, restored with the rest)
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
focus() { dumpsys window | grep mCurrentFocus | tail -1; }
k() { case "$(focus)" in *com.shelf.launcher.qa*) input keyevent $1; sleep ${2:-0.8};; *) echo "ABORT $(focus)" >> $L; echo DONE >> $L; exit 2;; esac; }
A --ez voltcap_test_nolaunch true; sleep 3
A --es voltcap_test_snapshot save; sleep 2; k 97 1
A --ez voltcap_test_preview true; sleep 1
A --es voltcap_test_fixture "names=real,art=portrait,meta=none"; sleep 3
# (the case picked for all applies only where its R1 tile does: Game shelf. PS5 / Xbox One are not in that list — LookStyles.cases()
# is MediaCovers' consoles — and no ROM folder maps to them, so they can't be reached with the QA library: reported, not drawn.)
A --es voltcap_test_layout SHELF --es voltcap_test_game "Shadow of the Colossus"; sleep 4
for C in PS2own Xbox360 PS3 PS4; do
  V=$C; [ $C = PS2own ] && V=""
  A --es voltcap_test_pref "case_style=$V"; sleep 1.5
  for pose in "0|0" "45|6" "90|0" "180|0" "25|40"; do
    k 97 0.6; A --es voltcap_test_inspect "Shadow_of_the_Colossus||${pose%|*}|${pose#*|}"; sleep 3.2
    screencap -p "$O/${C}_$(echo $pose | tr '|' '_').png"
  done
  A --es voltcap_test_pref "inspect_open_at_start=true"; sleep 0.8
  k 97 0.6; A --es voltcap_test_inspect "Shadow_of_the_Colossus||20|4"; sleep 4.5; screencap -p "$O/${C}_open.png"
  A --es voltcap_test_pref "inspect_open_at_start=false"; sleep 0.8
  echo "case $C" >> $L
done
k 97 0.8; A --es voltcap_test_pref "case_style="; sleep 1
A --es voltcap_test_fixture off; sleep 1; A --ez voltcap_test_preview false; sleep 0.5
k 97 0.8; A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cat $F/voltcap_test_snapshot_verify.txt 2>/dev/null)" >> $L
echo "launches=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH') real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L
