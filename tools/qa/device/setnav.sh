# setnav.sh REPS — Settings (main menu) navigation A/B for update 456: ▼x10 ▲x10 (vertical) and ▶x4 ◀x4 (tabs) per mode.
# Prints frames, missed vsyncs, max gap (QaTrace cadence) and gfxinfo janky % / GPU 50th-90th. Nothing saved.
P=com.shelf.launcher; R=${1:-3}; A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.25}; }
g0() { dumpsys gfxinfo $P reset > /dev/null 2>&1; logcat -c; }
m() { c=$(logcat -d -s VoltcapQA | grep -o "cadence n=[0-9]* missed=[0-9]* maxgap=[0-9]*" | awk '{split($2,n,"=");split($3,x,"=");split($4,g,"=");N+=n[2];M+=x[2];if(g[2]>G)G=g[2]} END{printf "frames=%d missed=%d maxgap=%d",N,M,G}')
      gi=$(dumpsys gfxinfo $P | grep -E "Janky frames:|50th gpu percentile|90th gpu percentile" | head -3 | sed 's/  */ /g' | tr '\n' ' ')
      echo "$1 $c | $gi"; }
input keyevent KEYCODE_WAKEUP; sleep 1
for mode in false true; do
  A --ez voltcap_test_panelhold $mode; sleep 1
  for i in $(seq 1 $R); do
    A --es voltcap_test_settings About; sleep 2.5
    g0; for j in 1 2 3 4 5 6 7 8 9 10; do k DPAD_DOWN; done; for j in 1 2 3 4 5 6 7 8 9 10; do k DPAD_UP; done; sleep 0.4; m "hold=$mode vertical"
    g0; sleep 3; m "hold=$mode idle"
    k BUTTON_B 0.8; k BUTTON_B 1.5
  done
done
A --ez voltcap_test_panelhold true
