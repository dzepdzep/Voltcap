# Update 489 (D285) — open (A) and close (B) the game window N times on one layout / popup style and log, per cycle, the
# slowest frames (dumpsys gfxinfo) and any ANR. Preview hooks only (qa_nolaunch must be there); nothing launched or saved.
# sh cycle489.sh LABEL LAYOUT STYLE N [GAME] [details]  (details: Right + A opens the next action - Details - before closing)  -> /sdcard/pc/cyc/LABEL.txt
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pc/cyc; mkdir -p $OUT
L=$1; LAY=$2; ST=$3; N=${4:-10}; GAME=$5; MODE=$6
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
[ -f $F/qa_nolaunch ] || { echo "qa_nolaunch missing - stop"; exit 1; }
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; A --es voltcap_test_layout $LAY; sleep 3
A --es voltcap_test_pref "popup_style_by_layout=$LAY=$ST"; sleep 1
[ -n "$GAME" ] && { A --es voltcap_test_game "$GAME"; sleep 2.5; }
anr0=$(dumpsys dropbox | grep -c data_app_anr)
: > $OUT/$L.txt
i=0
while [ $i -lt $N ]; do
  dumpsys gfxinfo $P reset > /dev/null
  input keyevent KEYCODE_BUTTON_A; sleep 2.2
  [ "$MODE" = details ] && { input keyevent KEYCODE_DPAD_RIGHT; sleep 0.6; input keyevent KEYCODE_BUTTON_A; sleep 2.2; input keyevent KEYCODE_BUTTON_B; sleep 1.5; }
  input keyevent KEYCODE_BUTTON_B; sleep 2.2
  g=$(dumpsys gfxinfo $P)
  p99=$(echo "$g" | grep -m1 "99th percentile" | awk '{print $3}')
  jk=$(echo "$g" | grep -m1 "Janky frames:" | awk '{print $3}')
  fr=$(echo "$g" | grep -m1 "Total frames rendered" | awk '{print $4}')
  anr=$(( $(dumpsys dropbox | grep -c data_app_anr) - anr0 ))
  fg=$(dumpsys window | grep -m1 mCurrentFocus | grep -c "Application Not Responding\|isn't responding")
  echo "cycle $i frames=$fr janky=$jk p99=$p99 anr=$anr" | tee -a $OUT/$L.txt
  [ $anr -gt 0 ] && { echo "ANR - stop" | tee -a $OUT/$L.txt; break; }
  i=$((i+1))
done
