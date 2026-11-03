P=com.shelf.launcher
input keyevent 224; am start -n $P/.MainActivity >/dev/null; sleep 7; input keyevent 97; sleep 2; logcat -c
for s in "Fool|gb|0|0" "Silver_Version|gbc_dual~m:pkmn_silver|0|0" "Prism_Version|gbc|0|0" "Emerald_Kaizo|gba|0|0"; do am start -n $P/.MainActivity --es voltcap_test_inspect "$s" >/dev/null; sleep 14; input keyevent 97; sleep 1; input keyevent 97; sleep 1; done
logcat -d -s VoltcapQA | grep photoshell | sed 's/https[^ ]*//' > /sdcard/warm.txt
dumpsys meminfo $P | grep -E "TOTAL PSS|TOTAL:" | head -2 >> /sdcard/warm.txt
rm -rf /sdcard/p462e; sh /data/local/tmp/p462u.sh /sdcard/p462e "Silver_Version|media" "Silver_Version|gbc_dual~m:pkmn_silver" "Fool|gb" "Prism_Version|gbc" "Crystal_Version|media" "Advance_Wars_2|media" "Emerald_Kaizo|gba"
cd /sdcard/p462e && tar cf /sdcard/p462e.tar *.png subjects.txt
