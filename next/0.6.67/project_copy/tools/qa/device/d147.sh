# d147.sh TAG LAYOUT "OPEN KEYS" "AFTER KEYS" — D147 check on the Thor: preview LAYOUT, press OPEN KEYS (the layout's own way to its
# dashboard button), A, ▼ (onto the dashboard: the return button should light), A (back), then AFTER KEYS. Both screens captured.
P=com.shelf.launcher/.MainActivity; D=/sdcard/pp/$1; mkdir -p $D
cap() { screencap -d 4630946441858561667 -p $D/${1}_top.png; screencap -d 4630946482288158084 -p $D/${1}_bot.png; }
k() { for x in $1; do input keyevent KEYCODE_$x; sleep 0.35; done; }
am start -n $P --es voltcap_test_twindash off >/dev/null 2>&1; sleep 0.5
am start -n $P --es voltcap_test_layout $2 >/dev/null 2>&1; sleep 3.5
cap ${2}_0start
k "$3"; sleep 0.5; cap ${2}_1onbutton
k BUTTON_A; sleep 2; cap ${2}_2dashboard
k DPAD_DOWN; sleep 0.8; cap ${2}_3backlit
k BUTTON_A; sleep 1.5; cap ${2}_4returned
k "$4"; sleep 0.6; cap ${2}_5after
echo "$2 done" >> $D/log.txt
