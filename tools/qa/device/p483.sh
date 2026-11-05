# Update 483 (A5) — "All games" count on the category screen (B on the home) and the Dashboard's Library line. Preview only.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pc/p483; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.9}; }
pk() { for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 2; done; }
dump() { rm -f $F/qa_clip/p483_$1.json; A --es voltcap_test_clip p483_$1; sleep 2.5; cp $F/qa_clip/p483_$1.json $OUT/$1.json; }
input keyevent KEYCODE_WAKEUP; sleep 1
A --ez voltcap_test_preview true --es voltcap_test_layout CLASSIC; sleep 4; pk
A --es voltcap_test_category ALL; sleep 3; pk
k BUTTON_B 2.5; dump cats; screencap -p $OUT/cats.png; k BUTTON_B 2
A --ez voltcap_test_preview true --es voltcap_test_layout DASH; sleep 4; pk; dump dash; screencap -p $OUT/dash.png
A --ez voltcap_test_preview false; sleep 1
grep -o '"label":"[^"]*games*"' $OUT/*.json | sort -u > $OUT/labels.txt; echo DONE >> $OUT/labels.txt
