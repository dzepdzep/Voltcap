#!/system/bin/sh
# Update 516: the Xbox 360 / Xbox One / PS3 / PS4 / PS5 cases as those consoles' OWN games get them — isolated Voltcap QA app ONLY.
# Five stand-in files in the QA app's own fixture library (folders ps3 / ps4 / xbox360 / PS5 / XboxOne — the scanner keeps an
# unknown folder's name as the console), a QA-only rescan of that folder (voltcap_test_fixture_roms), the QA fixture's portrait
# art as every cover, each game held in Inspect at the front, 45°, the spine, the back, from above, and opened. The stand-ins are
# removed and the QA library rescanned at the end; settings snapshotted and restored.   sh xcase516.sh TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt; R=$F/roms
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
k() { f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) input keyevent $1; sleep ${2:-0.8};; *) log "ABORT $f"; echo DONE >> $L; exit 2;; esac; }
picker() { grep -q "^PICKER $(pidof $P)" $F/voltcap_qa_screen 2>/dev/null && { k 97 1.5; log "picker dismissed"; }; }
CS="ps3:PS3 ps4:PS4 xbox360:Xbox360 PS5:PS5 XboxOne:XboxOne"
log "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher)"; logcat -c
A --ez voltcap_test_nolaunch true; sleep 3; picker; A --es voltcap_test_snapshot save; sleep 2; k 97 1
for c in $CS; do d=${c%%:*}; n=${c#*:}; mkdir -p $R/$d; echo "voltcap qa fixture" > "$R/$d/QA Case $n (USA).iso"; done
A --ez voltcap_test_fixture_roms true; sleep 12
logcat -d -s VoltcapQA VoltcapTest | grep -iE "scan|games" | tail -3 >> $L
A --ez voltcap_test_preview true; sleep 1
A --es voltcap_test_fixture "names=real,art=portrait,meta=none"; sleep 3
for c in $CS; do n=${c#*:}
  # (Inspect finds the game in the list on screen: the game's own console list first — voltcap_test_game moves there.)
  k 97 0.6; A --es voltcap_test_layout SHELF --es voltcap_test_game "QA Case $n"; sleep 3.5; screencap -p "$O/${n}_home.png"
  for pose in "0|0" "45|6" "90|0" "180|0" "25|40"; do
    A --es voltcap_test_inspect "QA_Case_$n||${pose%|*}|${pose#*|}"; sleep 3.2
    screencap -p "$O/${n}_$(echo $pose | tr '|' '_').png"
  done
  A --es voltcap_test_pref "inspect_open_at_start=true"; sleep 0.8
  k 97 0.6; A --es voltcap_test_inspect "QA_Case_$n||20|4"; sleep 4.5; screencap -p "$O/${n}_open.png"
  A --es voltcap_test_pref "inspect_open_at_start=false"; sleep 0.8
  log "case $n $(logcat -d -s VoltcapTest | grep -c "QA Case $n")"
done
k 97 0.8; A --es voltcap_test_fixture off; sleep 1; A --ez voltcap_test_preview false; sleep 0.5
for c in $CS; do d=${c%%:*}; n=${c#*:}; rm -f "$R/$d/QA Case $n (USA).iso"; rmdir $R/$d; done
A --ez voltcap_test_fixture_roms true; sleep 12; k 97 0.8
A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cat $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"; log "roms: $(ls $R | tr '\n' ' ')"
log "launchesBlocked=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked') real=$(pidof com.shelf.launcher)"; echo DONE >> $L
