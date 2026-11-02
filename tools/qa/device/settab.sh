# settab.sh PAGE — Settings-only A/B of the update 456 panel hold on one Settings page (idle 4 s), 2 reps per mode.
P=com.shelf.launcher; A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
m() { logcat -d -s VoltcapQA | grep -o "cadence n=[0-9]* missed=[0-9]*" | awk -v a="$1" '{split($2,n,"=");N+=n[2]} END{printf "%s frames=%d\n",a,N}'; }
input keyevent KEYCODE_WAKEUP; sleep 1
for mode in false true false true; do
  A --ez voltcap_test_panelhold $mode; sleep 1; A --es voltcap_test_settings $1; sleep 2.5; logcat -c; sleep 4; m "hold=$mode $1"
  input keyevent KEYCODE_BUTTON_B; sleep 0.8; input keyevent KEYCODE_BUTTON_B; sleep 1.5
done
A --ez voltcap_test_panelhold true
