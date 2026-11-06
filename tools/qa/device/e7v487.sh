# Update 487 — E7 verification on the Thor (owner-approved changes): Cover details paged story (D263 / D281), Twin window
# story pages by R2 / L2 (D265), popup Play / keys (Tiles, Case Flow, Cinema, Classic). Preview hooks only, nothing saved,
# launches blocked (qa_nolaunch). Screens + QaClip dumps into /sdcard/pc/e7v487.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pc/e7v487; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
pk() { for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 2; done; }
shot() { screencap -p $OUT/$1.png; echo "e7v_$1" > $F/qa_clip_req; sleep 1.2; }
G="World Is Not Enough"
L=${1:-COVERDETAILS}
input keyevent 224; A --ez voltcap_test_preview true --es voltcap_test_layout $L; sleep 5; pk
A --es voltcap_test_game "$G"; sleep 4; input keyevent 224
case $L in
  COVERDETAILS) shot cd_a; sleep 7; shot cd_b; sleep 7; shot cd_c ;;
  TWIN_DESK) k BUTTON_A 2.5; shot tw_a; k BUTTON_R2 1.2; shot tw_b; sleep 9; shot tw_c; k BUTTON_L2 1.2; shot tw_d; k BUTTON_B 1.5 ;;
  *) k BUTTON_A 2.5; shot pop_$L; k DPAD_RIGHT 1; shot pop_${L}_r; k BUTTON_B 1.5 ;;
esac
echo "DONE $L" >> $OUT/log.txt
