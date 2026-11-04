# cp1.sh — Continue playing on the Thor (update 451): tile launch path, return, first press after return.
P=com.shelf.launcher/.MainActivity; F=/sdcard/Android/data/com.shelf.launcher/files; O=/sdcard/cp451; mkdir -p $O
A() { am start -n $P "$@" > /dev/null 2>&1; }
probe() { A --es voltcap_test_appinfo psplay.grill.com,com.asobi.droid; sleep 2; sed 's/installed.*continue_opt_in=/opt=/' $F/voltcap_qa_appinfo.txt; }
task() { dumpsys activity activities | grep -E "^ *\* Task\{.*$1" | head -1 | grep -o "#[0-9]*"; }
top() { dumpsys activity activities | grep -m1 topResumedActivity | sed 's/.*u0 //; s/ t[0-9]*}//'; }
A; sleep 4; input keyevent KEYCODE_BUTTON_B; sleep 1
echo "== before"; probe
A --es voltcap_test_category S:RECENT; sleep 3
for i in 1 2 3 4; do input keyevent KEYCODE_DPAD_LEFT; sleep 0.6; done
echo "== tile launch PXPlay (task before $(task psplay))"; logcat -c
input keyevent KEYCODE_BUTTON_A; sleep 6
echo "top=$(top) task=$(task psplay) companion=$(dumpsys activity activities | grep -c 'CompanionActivity')"
logcat -d -s VoltcapQA | grep -o "LAUNCH asked [a-zA-Z.]* [a-z.]*" | sort | uniq -c
screencap -d 4630946441858561667 -p $O/launched_top.png
input keyevent KEYCODE_HOME; sleep 4
echo "== after return"; probe
screencap -d 4630946482288158084 -p $O/after_bot.png
echo "== one A press right after return"; logcat -c
input keyevent KEYCODE_BUTTON_A; sleep 5
echo "top=$(top)"; logcat -d -s VoltcapQA | grep -o "LAUNCH asked [a-zA-Z.]* [a-z.]*" | sort | uniq -c
input keyevent KEYCODE_HOME; sleep 3
echo DONE
