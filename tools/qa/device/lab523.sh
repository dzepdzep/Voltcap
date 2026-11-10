#!/system/bin/sh
# Batch 523: popup design lab before / after - the same popups in each variant (PopupLab, voltcap_test_popuplab), isolated
# QA app ONLY, stand-in library, preview (nothing saved), settings snapshotted / restored.   sh lab523.sh TAG [GAME]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; GAME=${2:-Mario Kart 64}; O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true "$@" >/dev/null 2>&1; }
k() { for w in 1 2 3 4 5 6 7 8 9 10; do f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *Application*) ;; *com.shelf.launcher.qa*) break;; *=null*) input keyevent KEYCODE_WAKEUP;; esac; sleep 0.5; done
  case "$f" in *Application*) echo "ABORT focus=$f" >> $L; am force-stop com.shelf.launcher.qa; exit 2;; *com.shelf.launcher.qa*) input keyevent $1;; *) echo "ABORT focus=$f" >> $L; am force-stop com.shelf.launcher.qa; exit 2;; esac; }
pk() { grep -q '^CRASH' $F/voltcap_qa_screen 2>/dev/null && { k KEYCODE_BUTTON_B; sleep 2; }; grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { k KEYCODE_BUTTON_B; sleep 1.5; }; }
input keyevent KEYCODE_WAKEUP; A --ez voltcap_test_nolaunch true; sleep 4; pk
A --es voltcap_test_snapshot save; sleep 2
# layout:style - the scenes (own style) and the row styles on the Voltcap row
CASES=${CASES:-"CLASSIC:OWN TILES:OWN STORAGE:OWN HERO:OWN SPOT:OWN CLASSIC:ROW_REVEAL CLASSIC:SHARED CLASSIC:BANNER"}
for V in ${VS:-base current_w current_wa inter_wa manrope_wa}; do
  case $V in
    base) SPEC=off;; current_w) SPEC="font=current,weights=on";; current_wa) SPEC="font=current,weights=on,actions=on,storage=on";;
    inter_wa) SPEC="font=inter,weights=on,actions=on,storage=on";; manrope_wa) SPEC="font=manrope,weights=on,actions=on,storage=on";;
  esac
  A --es voltcap_test_popuplab "$SPEC"; sleep 1.5
  for C in $CASES; do
    LY=${C%%:*}; ST=${C##*:}
    A --es voltcap_test_pref "popup_style_by_layout=$LY=$ST"; sleep 0.8
    A --ez voltcap_test_preview true; A --es voltcap_test_layout $LY --es voltcap_test_category ALL --es voltcap_test_game "$GAME"; sleep 3.5; pk
    k KEYCODE_BUTTON_A; sleep 2.4; screencap -p $O/${LY}_${ST}_$V.png
    k KEYCODE_BUTTON_B; sleep 1.4
    echo "$V $C" >> $L
  done
done
A --es voltcap_test_popuplab off; sleep 1; A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null) real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L
