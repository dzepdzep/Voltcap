# dualreg.sh TAG — update 451 short dual-screen regression on the Thor (previews only, launches blocked): per two-screen layout the
# dashboard is opened (voltcap_test_twindash, as its own button does), ▼ must light the return button, A must come back, ▶ must move.
P=com.shelf.launcher; OUT=/sdcard/dr/$1; rm -rf $OUT; mkdir -p $OUT
D0=4630946441858561667; D1=4630946482288158084
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
S() { screencap -d $D0 -p $OUT/${1}_top.png; screencap -d $D1 -p $OUT/${1}_bot.png; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
A --ez voltcap_test_nolaunch true; A --ez voltcap_test_preview true; sleep 1
for L in TWIN_DESK TWIN_ICONS TWIN_SHOWCASE POCKET_CLASSIC POCKET_RIBBON; do
  A --es voltcap_test_twindash off; sleep 0.5; A --es voltcap_test_layout $L; sleep 3.5
  k SCROLL_LOCK 0.5   # (a key with no job first: the first key after an intent can go to the system's own second-screen window)
  A --es voltcap_test_twindash on; sleep 2
  k SCROLL_LOCK 0.4; S ${L}_1dash
  k DPAD_DOWN 1; S ${L}_2down
  k BUTTON_A 1.5; S ${L}_3back
  k DPAD_RIGHT 0.8; S ${L}_4moved
  echo "$L done" >> $OUT/log.txt
done
A --es voltcap_test_twindash off; A --ez voltcap_test_preview false; A --ez voltcap_test_nolaunch false
echo "launch blocked: $(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')" >> $OUT/log.txt; echo DONE >> $OUT/log.txt
