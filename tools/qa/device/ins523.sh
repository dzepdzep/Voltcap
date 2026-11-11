#!/system/bin/sh
# Batch 523: Inspect gesture test on the isolated QA app ONLY, with REAL multi-touch on the RP Classic's panel (mt523.sh).
#   sh ins523.sh open GAME [LAYOUT]   - settings snapshot, preview, the game's window, its Inspect key tapped; shot ins_open
#   sh ins523.sh shot NAME            - a screenshot
#   sh ins523.sh close                - B twice, preview off, settings restored
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; O=/sdcard/pc/ins523; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
case $1 in
  open) input keyevent KEYCODE_WAKEUP; A --ez voltcap_test_nolaunch true; sleep 3
    grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; }
    A --es voltcap_test_snapshot save; sleep 2; A --ez voltcap_test_preview true; sleep 1
    A --es voltcap_test_layout ${3:-GRID}; sleep 3; A --es voltcap_test_game "$2"; sleep 2
    input keyevent KEYCODE_BUTTON_A; sleep 2.5
    rm -f $F/qa_clip/ins_main.json; echo ins_main > $F/qa_clip_req; sleep 1.2
    r=$(grep -oE '"label":"Inspect","kind":"control","full":\[[0-9,-]*\],"shown":\[[0-9,-]*\]' $F/qa_clip/ins_main.json | head -1 | sed 's/.*"shown":\[//;s/\]//')
    echo "inspect key: $r" >> $L
    if [ -n "$r" ]; then set -- $(echo $r | tr ',' ' '); input tap $(( $1 + $3 / 2 )) $(( $2 + $4 / 2 )); fi
    sleep 3; screencap -p $O/ins_open.png; logcat -c; echo "open $(date +%T)" >> $L;;
  shot) screencap -p $O/$2.png;;
  close) input keyevent KEYCODE_BUTTON_B; sleep 1.5; input keyevent KEYCODE_BUTTON_B; sleep 1.5
    A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
    echo "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null) real=$(pidof com.shelf.launcher)" >> $L;;
esac
true
