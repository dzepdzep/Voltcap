# Update 482 (B5) — Cardboard box with one box: ▶ past the last case and ◀ past the first (Endless on). Preview only.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pc/box5; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.9}; }
pk() { for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 2; done; }
input keyevent KEYCODE_WAKEUP; sleep 1
A --ez voltcap_test_preview true --es voltcap_test_layout BOX; sleep 4; pk
A --es voltcap_test_category ALL; sleep 3; pk; sleep 1
logcat -c; for i in 1 2 3 4 5 6 7; do k DPAD_RIGHT; done; for i in 1 2 3 4 5 6 7; do k DPAD_LEFT; done
logcat -d -s VoltcapQA | grep -o ' focus [0-9]* ' > $OUT/focus.txt
grep -c '' $OUT/focus.txt >> $OUT/focus.txt
A --ez voltcap_test_preview false; sleep 1
echo DONE >> $OUT/focus.txt
