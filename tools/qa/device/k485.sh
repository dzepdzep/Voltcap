# Update 485 (KONKR) — E3 / E4 / E5 Pokemon Silver in Case Flow, Case stacks, Game shelf and Inspect (front, angles, spine,
# back); the case's media sources; A5 GBA logos. Preview / QA hooks only, nothing saved, no game started.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pc/k485; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
w() { input keyevent 224; }
pk() { for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 2; done; }
G="${GAME:-Silver}"
w; sleep 1
for L in CASEFLOW STACKS SHELF; do
  w; A --ez voltcap_test_preview true --es voltcap_test_layout $L; sleep 5; pk
  w; A --es voltcap_test_game "$G"; sleep 6; w; sleep 1
  screencap -p $OUT/$L.png
done
for pose in 0:0 25:8 90:0 180:0; do
  w; A --es voltcap_test_inspect "$G||${pose%%:*}|${pose##*:}"; sleep 6; w
  screencap -p $OUT/inspect_${pose%%:*}.png; k BUTTON_B 1.5
done
rm -f $F/media_dump.txt; A --es voltcap_test_mediadump "$G"; sleep 4
sed 's/https[^ ]*//g' $F/media_dump.txt > $OUT/media_dump.txt 2>/dev/null; rm -f $F/media_dump.txt
for L in CUBE CROSS CHAN SIDE CONSOLES HUB; do
  w; A --ez voltcap_test_preview true --es voltcap_test_layout $L; sleep 5; pk
  w; A --es voltcap_test_category GBA; sleep 4; w; sleep 1
  screencap -p $OUT/logo_$L.png
done
A --ez voltcap_test_preview false; sleep 1
echo DONE > $OUT/log.txt
