# Update 488 (K3) — every physical Save Vault look x PS2 browser style on / off: the grid slot and the save up close, on the
# first save (any game) and a PS2 save if the vault has one (second Right-step). The look is set with voltcap_test_pref (the
# caller restores the owner's settings with voltcap_test_snapshot); the PS2 switch only for this session. Saves are only looked at.
# Run detached: setsid sh k3styles488.sh &   Results in /sdcard/pc/k3s, DONE in log.txt.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pc/k3s; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
for st in MEMORY_CARD SD_CARD FLOPPY CAPSULE CARD_3D; do
  for ps in off on; do
    n=${st}_$ps
    A --es voltcap_test_pref "save_vault_style=$st"; sleep 1
    A --es voltcap_test_ps2browser $ps; sleep 1
    input keyevent 224; A --es voltcap_test_settings Save_Vault; sleep 5
    screencap -p $OUT/${n}_grid.png
    k BUTTON_A 3; screencap -p $OUT/${n}_detail.png
    k BUTTON_B 2; k BUTTON_B 2
    echo "$n done" >> $OUT/log.txt
  done
done
echo DONE >> $OUT/log.txt
