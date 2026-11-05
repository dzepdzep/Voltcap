# Update 485 (KONKR) — Pokemon Silver in Case Flow / Case stacks / Game shelf after the cover-shape fix, and GBA logos.
# The profile picker is closed (and checked) before every still. Preview / QA hooks only, nothing saved.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pc/k485b; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
w() { input keyevent 224 2>/dev/null; }
pk() { for i in 1 2 3 4 5; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || return 0; w; input keyevent KEYCODE_BUTTON_B 2>/dev/null; sleep 2.5; done; }
shot() { w; sleep 0.5; pk; grep -q '^HOME' $F/voltcap_qa_screen && screencap -p $OUT/$1.png || echo "$1 NOT HOME $(cut -c1-8 $F/voltcap_qa_screen)" >> $OUT/log.txt; }
G="${GAME:-Silver}"
w; sleep 1; pk
for L in CASEFLOW STACKS SHELF; do
  w; A --ez voltcap_test_preview true --es voltcap_test_layout $L; sleep 5; pk
  w; A --es voltcap_test_game "$G"; sleep 7; shot $L
done
for L in CUBE CROSS CHAN SIDE CONSOLES HUB; do
  w; A --ez voltcap_test_preview true --es voltcap_test_layout $L; sleep 5; pk
  w; A --es voltcap_test_category GBA; sleep 4; shot logo_$L
done
A --ez voltcap_test_preview false; sleep 1
echo DONE >> $OUT/log.txt
