# ptab.sh TAG — Settings A/B for update 457: one perfetto trace per mode (voltcap_test_settingsflat false / true), Settings Home page, ▼x10 ▲x10.
P=com.shelf.launcher; A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
input keyevent KEYCODE_WAKEUP
for mode in false true; do
  A --ez voltcap_test_settingsflat $mode; sleep 1; A --es voltcap_test_settings Home; sleep 3
  perfetto -o /data/misc/perfetto-traces/$1_$mode.pftrace -t 8s -b 32mb gfx view sched > /dev/null 2>&1 &
  sleep 1.5
  for j in 1 2 3 4 5 6 7 8 9 10; do input keyevent KEYCODE_DPAD_DOWN; sleep 0.25; done; for j in 1 2 3 4 5 6 7 8 9 10; do input keyevent KEYCODE_DPAD_UP; sleep 0.25; done
  wait; input keyevent KEYCODE_BUTTON_B; sleep 0.8; input keyevent KEYCODE_BUTTON_B; sleep 1.5
done
A --ez voltcap_test_settingsflat true; ls -la /data/misc/perfetto-traces/ | grep $1
