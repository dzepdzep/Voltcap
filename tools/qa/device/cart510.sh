#!/system/bin/sh
# Update 510: cartridge pieces (library shell forced, preview only) in the QA app — screenshots to its own folder.
P=com.shelf.launcher.qa; O=/sdcard/Android/data/$P/files/c510; mkdir -p $O; rm -f $O/*
n=0
for s in "Metroid_Fusion|gba" "Tetris|gb" "Mario_Kart_64|n64" "Pokemon_-_Crystal_Version|gbc"; do
  n=$((n+1)); input keyevent 97; sleep 0.6
  am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_inspect "$s|30|0" >/dev/null; sleep 3
  screencap -p $O/cart$n.png
done
input keyevent 97; ls $O
