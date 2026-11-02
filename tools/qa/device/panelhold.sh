# panelhold.sh TAG REPS — update 456 A/B: the home background held under the app drawer and Settings (voltcap_test_panelhold).
# Per mode (off = before, on = after): drawer open + idle 4 s, drawer scroll ▼x8 ▲x8, Settings open + idle 4 s, Settings ▼x6 ▲x6.
# Prints frames / missed vsyncs / max gap (QaTrace cadence) and gfxinfo GPU 50th/90th percentile + janky % per phase.
# The drawer is reached through a Tile home PREVIEW (dock ▼ A), as in perf.sh; nothing is saved; view state restored at the end.
P=com.shelf.launcher; OUT=/sdcard/pv/$1; rm -rf $OUT; mkdir -p $OUT; R=${2:-3}
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.6}; }
g0() { dumpsys gfxinfo $P reset > /dev/null 2>&1; logcat -c; }
m() { c=$(logcat -d -s VoltcapQA | grep -o "cadence n=[0-9]* missed=[0-9]* maxgap=[0-9]*" | awk '{split($2,n,"=");split($3,x,"=");split($4,g,"=");N+=n[2];M+=x[2];if(g[2]>G)G=g[2]} END{printf "frames=%d missed=%d maxgap=%d",N,M,G}')
      gi=$(dumpsys gfxinfo $P | grep -E "Janky frames:|50th gpu percentile|90th gpu percentile" | head -3 | sed 's/  */ /g' | tr '\n' ' ')
      echo "$1 $c | $gi" >> $OUT/log.txt; }
input keyevent KEYCODE_WAKEUP; sleep 1
A --es voltcap_test_snapshot save; sleep 2; A --ez voltcap_test_nolaunch true; sleep 1
for mode in false true; do
  A --ez voltcap_test_panelhold $mode; sleep 1
  for i in $(seq 1 $R); do
    A --ez voltcap_test_preview true; A --es voltcap_test_layout TILES; sleep 4; k DPAD_DOWN 0.5; k BUTTON_A 1.5
    g0; sleep 4; m "hold=$mode drawer_idle"
    g0; for j in 1 2 3 4 5 6 7 8; do k DPAD_DOWN 0.25; done; for j in 1 2 3 4 5 6 7 8; do k DPAD_UP 0.25; done; sleep 0.5; m "hold=$mode drawer_scroll"
    k BUTTON_B 1.2; A --ez voltcap_test_preview false; sleep 2
    A --es voltcap_test_settings Game_look; sleep 2.5
    g0; sleep 4; m "hold=$mode settings_idle"
    g0; for j in 1 2 3 4 5 6; do k DPAD_DOWN 0.3; done; for j in 1 2 3 4 5 6; do k DPAD_UP 0.3; done; sleep 0.5; m "hold=$mode settings_nav"
    k BUTTON_B 0.8; k BUTTON_B 1.4
    g0; sleep 3; m "hold=$mode home_idle"
  done
done
A --ez voltcap_test_panelhold true; A --ez voltcap_test_nolaunch false; sleep 1
A --es voltcap_test_snapshot restore; sleep 3; echo "restore: $(cat /sdcard/Android/data/$P/files/voltcap_test_snapshot_verify.txt)" >> $OUT/log.txt; echo DONE >> $OUT/log.txt
