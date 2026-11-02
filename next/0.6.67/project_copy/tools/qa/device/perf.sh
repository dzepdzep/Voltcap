# perf.sh TAG REPS — frame cadence per action (QaTrace "cadence n= missed= maxgap=" lines, 1 s windows) in the owner's own layout.
# Actions: R1 open/close · Settings (hook) open/close · R2 x4 then L2 x4 (back where it was) · game window A/B (launch blocked) ·
# return from Android Settings (HOME) · drawer scroll (Tile home preview, dock ▼ A, ▼ x8). Prints per action: frames, missed vsyncs, max gap.
P=com.shelf.launcher; OUT=/sdcard/pv/$1; rm -rf $OUT; mkdir -p $OUT; R=${2:-3}
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.6}; }
m() { logcat -d -s VoltcapQA | grep -o "cadence n=[0-9]* missed=[0-9]* maxgap=[0-9]*" | awk -v a="$1" '{split($2,n,"=");split($3,x,"=");split($4,g,"=");N+=n[2];M+=x[2];if(g[2]>G)G=g[2]} END{printf "%s frames=%d missed=%d maxgap=%d\n",a,N,M,G}' >> $OUT/log.txt; logcat -c; }
A --es voltcap_test_snapshot save; sleep 2; A --ez voltcap_test_nolaunch true; sleep 1; k SCROLL_LOCK 0.3; logcat -c
for i in $(seq 1 $R); do k BUTTON_R1 1.6; k BUTTON_B 1.4; m r1; done
for i in $(seq 1 $R); do A --es voltcap_test_settings Game_look; sleep 2.2; k BUTTON_B 0.8; k BUTTON_B 1.4; m settings; done
for i in $(seq 1 $R); do for j in 1 2 3 4; do k BUTTON_R2 0.5; done; for j in 1 2 3 4; do k BUTTON_L2 0.5; done; sleep 0.8; m l2r2; done
for i in $(seq 1 $R); do k BUTTON_A 1.6; k BUTTON_B 1.6; m popup; done
for i in $(seq 1 $R); do am start -a android.settings.SETTINGS > /dev/null 2>&1; sleep 2; logcat -c; input keyevent KEYCODE_HOME; sleep 2.5; m return; done
A --ez voltcap_test_preview true; A --es voltcap_test_layout TILES; sleep 4; k DPAD_DOWN 0.5; k BUTTON_A 1.5; logcat -c
for i in $(seq 1 $R); do for j in 1 2 3 4 5 6 7 8; do k DPAD_DOWN 0.25; done; for j in 1 2 3 4 5 6 7 8; do k DPAD_UP 0.25; done; sleep 0.5; m drawer; done
k BUTTON_B 1; A --ez voltcap_test_preview false; A --ez voltcap_test_nolaunch false; sleep 1
A --es voltcap_test_snapshot restore; sleep 3; echo "restore: $(cat /sdcard/Android/data/$P/files/voltcap_test_snapshot_verify.txt)" >> $OUT/log.txt; echo DONE >> $OUT/log.txt
