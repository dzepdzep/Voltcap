#!/system/bin/sh
# Batch 523: Save Vault looks with saves of games that are here, games that are NOT here (with a look another device
# recorded, and with none), isolated QA app ONLY, `voltcap_test_vault_fixture` (display only: no save read / written,
# nothing synced). Per look: the grid, a not-here save opened (A = DPAD_CENTER), back. Settings snapshotted / restored.
#   sh vault523.sh TAG ["LOOKS"]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
LOOKS=${2:-"CLASSIC ICONS PS2 MEMORY_CARD SD_CARD FLOPPY CAPSULE CARD_3D"}
A() { am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true "$@" >/dev/null 2>&1; }
pk() { grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; }; }
input keyevent KEYCODE_WAKEUP; A --ez voltcap_test_nolaunch true; sleep 4; pk
A --es voltcap_test_snapshot save; sleep 2
for LK in $LOOKS; do for BA in before after; do
  N=${LK}_$BA; B=false; [ $BA = before ] && B=true
  A --es voltcap_test_pref "save_vault_style=$LK"; sleep 1
  A --ez voltcap_test_vault_fixture true --ez voltcap_test_vault_before $B --es voltcap_test_settings Save_Vault; sleep 5
  screencap -p $O/${N}_grid.png
  # the last row holds the games that aren't here: to the end, then open one
  for i in 1 2 3 4 5 6 7 8 9 10; do input keyevent KEYCODE_DPAD_RIGHT; sleep 0.25; done; sleep 1; screencap -p $O/${N}_grid_end.png
  input keyevent KEYCODE_DPAD_CENTER; sleep 2.5; screencap -p $O/${N}_open.png
  input keyevent KEYCODE_BUTTON_B; sleep 1.5; input keyevent KEYCODE_BUTTON_B; sleep 1.5
  echo "$N" >> $L
done; done
A --ez voltcap_test_vault_fixture false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null) real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L
