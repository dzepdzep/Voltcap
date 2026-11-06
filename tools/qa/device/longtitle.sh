# longtitle.sh TAG "LAYOUTS" — per test game (long titles / maybe no art) x layout: window collapsed (c) and More open (e) stills.
# Owner's view state saved first and restored + verified at the end; preview mode; launches blocked.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pv/$1; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
A --es voltcap_test_snapshot save; sleep 2; cp $F/voltcap_test_snapshot_view.json $OUT/owner_view.json
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 1
n=0
for G in "GBA@/storage/emulated/0/ROMs/gba/Super Mario Advance 4 - Super Mario Bros. 3 (USA) (Rev 1) (Switches - RetroAchievements Edition).gba" \
         "GBC@/storage/emulated/0/ROMs/gbc/Pokemon Trading Card Game 2 - The Invasion of Team GR! (Japan) (Es) (v3.0) (Shinichi999).gbc"; do
  n=$((n+1)); plat=${G%%@*}; path=${G#*@}
  printf '{"platform":"%s","special":"","focus":"%s","profile":0}' "$plat" "$path" > $F/voltcap_test_snapshot_view.json
  A --es voltcap_test_snapshot restore; sleep 3; cat $F/voltcap_test_snapshot_verify.txt >> $OUT/log.txt; echo >> $OUT/log.txt
  for L in $2; do
    A --es voltcap_test_layout $L; sleep 3; input keyevent KEYCODE_SCROLL_LOCK; sleep 0.3
    input keyevent KEYCODE_BUTTON_A; sleep 1.8; screencap -p $OUT/g${n}_${L}_c.png
    A --es voltcap_test_winmore on; sleep 1.2; screencap -p $OUT/g${n}_${L}_e.png
    input keyevent KEYCODE_BUTTON_B; sleep 0.9; input keyevent KEYCODE_BUTTON_B; sleep 1.2
  done
done
A --ez voltcap_test_preview false; sleep 1
cp $OUT/owner_view.json $F/voltcap_test_snapshot_view.json
A --es voltcap_test_snapshot restore; sleep 3; echo "owner restore: $(cat $F/voltcap_test_snapshot_verify.txt)" >> $OUT/log.txt
echo DONE >> $OUT/log.txt
