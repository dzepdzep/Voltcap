# Update 487 (K3) — Save Vault: the grid slot vs the save up close, in the owner's own vault settings (nothing changed,
# no save data touched: the vault is only looked at). A opens a save's window, B closes it.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pc/k3vault; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
input keyevent 224; A --es voltcap_test_settings Save_Vault; sleep 5
screencap -p $OUT/grid${1}.png
k BUTTON_A 3; screencap -p $OUT/detail${1}.png
k BUTTON_B 2; k DPAD_RIGHT 1; k DPAD_RIGHT 1; screencap -p $OUT/grid${1}b.png
k BUTTON_A 3; screencap -p $OUT/detail${1}b.png
k BUTTON_B 2; k BUTTON_B 2
echo done
