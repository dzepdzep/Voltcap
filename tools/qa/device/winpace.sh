# winpace.sh MODE — Settings (main menu) ▼x10 ▲x10 with voltcap_test_panelhold MODE; per window (gfxinfo framestats):
# frames, frames over 1.5 vsync since the previous (while scrolling, gaps < 250 ms), worst gap, median GPU ms.
P=com.shelf.launcher; A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
input keyevent KEYCODE_WAKEUP; A --ez voltcap_test_panelhold $1; sleep 1; A --es voltcap_test_settings About; sleep 2.5
dumpsys gfxinfo $P reset > /dev/null
for j in 1 2 3 4 5 6 7 8 9 10; do input keyevent KEYCODE_DPAD_DOWN; sleep 0.25; done; for j in 1 2 3 4 5 6 7 8 9 10; do input keyevent KEYCODE_DPAD_UP; sleep 0.25; done
dumpsys gfxinfo $P framestats | awk -F, '/ViewRootImpl@/{w=$0} /^Flags,/{for(i=1;i<=NF;i++){if($i=="IntendedVsync")iv=i; if($i=="FrameCompleted")fc=i}; next}
 /---PROFILEDATA---/{if(p){ if(n>0){printf "%s frames=%d late=%d worst_gap=%.0fms dur_mean=%.1fms over16=%d\n", substr(w,1,80), n, late, worst/1e6, sum/n/1e6, over}; n=0; late=0; worst=0; prev=0; sum=0; over=0}; p=!p; next}
 p && $1=="0" {v=$iv; if(prev>0){g=v-prev; if(g>25000000 && g<250000000) late++; if(g<250000000 && g>worst) worst=g}; prev=v; n++; x=$fc-$iv; sum+=x; if(x>16700000) over++}' 
A --es voltcap_test_settings About; input keyevent KEYCODE_BUTTON_B; sleep 0.8; input keyevent KEYCODE_BUTTON_B; sleep 1; A --ez voltcap_test_panelhold true
