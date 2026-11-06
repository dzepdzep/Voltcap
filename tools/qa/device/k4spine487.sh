# Update 487 (K4) — generated / custom spines while scrolling: layout L, category C; 12 quick steps right, 12 back left
# (returning to games already drawn), recorded at the screen's rate. Preview hooks only; nothing saved or launched.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; L=$1; C=$2; T=${3:-0.12}; OUT=/sdcard/pc/k4spine; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
input keyevent 224; A --ez voltcap_test_preview true --es voltcap_test_layout $L; sleep 5
for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; input keyevent KEYCODE_BUTTON_B; sleep 2; done
A --es voltcap_test_category "$C"; sleep 4; input keyevent 224
rm -f $OUT/${L}_$C.mp4; (screenrecord --time-limit 12 --bit-rate 20000000 $OUT/${L}_$C.mp4 &); sleep 1.2
for i in $(seq 1 12); do input keyevent KEYCODE_DPAD_RIGHT; sleep $T; done; sleep 1.5
for i in $(seq 1 12); do input keyevent KEYCODE_DPAD_LEFT; sleep $T; done; sleep 3
ls -l $OUT/${L}_$C.mp4
