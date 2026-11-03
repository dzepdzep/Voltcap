# verify461.sh — update 461 checks on the staging build (hooks on): label fronts (Inspect hook) and the popup combos fixed by WindowShade.
P=com.shelf.launcher; O=/sdcard/q461v; rm -rf $O; mkdir -p $O
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
input keyevent 224
A --es voltcap_test_snapshot save; sleep 2
A --ez voltcap_test_nolaunch true; sleep 1
n=0
for spec in "Pokemon_Fool|gb|0|0" "Pokemon_Fool|gbc|0|0" "Pokemon_Silver|gb|0|0" "Pokemon_Silver|gbc|0|0" "Pokemon_Kalos|gbc|0|0" "Pokemon_Prism|gbc|0|0" "Pokemon_Crystal_Kaizo|gbc|0|0" "Pokemon_Crystal_Clear|gbc|0|0" "Pokemon_Emerald_Kaizo|gba|0|0" "Advance_Wars_2|gba|0|0" "Super_Smash|n64|0|0" "Pokemon_Snap|n64|0|0"; do
  n=$((n+1)); input keyevent 224
  A --es voltcap_test_inspect "$spec"; sleep 7
  screencap -p $O/label_$(echo $spec | cut -d'|' -f1-2 | tr '|' '_').png
  k BUTTON_B 1.2; k BUTTON_B 1.2
done
A --ez voltcap_test_preview true; sleep 1
for L in GRID HERO TV CUBE FILESELECT SHOP; do
  input keyevent 224; A --es voltcap_test_layout $L; sleep 3.5
  for st in ROW_REVEAL SHARED; do
    A --es voltcap_test_pref "popup_style_by_layout=$L=$st"; sleep 1.5; k SCROLL_LOCK 0.3
    k BUTTON_A 1.7; screencap -p $O/pop_${L}_${st}.png; k BUTTON_B 1.4
  done
done
A --ez voltcap_test_nolaunch false; A --ez voltcap_test_preview false; sleep 1
A --es voltcap_test_snapshot restore; sleep 3; cat /sdcard/Android/data/$P/files/voltcap_test_snapshot_verify.txt > $O/restore.txt; echo DONE > $O/DONE
