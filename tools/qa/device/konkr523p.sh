# Batch 523p on the KONKR (GT78), isolated QA app ONLY: Coverflow details frame timing A/B (run once on 523o = blur, once on
# 523p = soft backdrop), Performance forced to Full for the run (QA hook, own intent) and back to Automatic after; Hero and
# Game shelf as references. Each layout twice, the first a warm-up.   sh konkr523p.sh TAG [FULL|LITE] [LAYOUTS]
export QA_FIX=true
P=com.shelf.launcher.qa; D=/data/local/tmp; T=$1; M=${2:-FULL}; LS=${3:-COVERDETAILS HERO SHELF}; S=/sdcard/pc/konkr$T.txt
echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
input keyevent KEYCODE_WAKEUP
am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true >/dev/null 2>&1; sleep 4
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_perf $M >/dev/null 2>&1; sleep 2
for R in a b; do for L in $LS; do sh $D/perf514.sh $T$R$L $L > /dev/null 2>&1; echo "perf $R $L $? $(date +%T)" >> $S; done; done
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_perf AUTO >/dev/null 2>&1; sleep 2
am force-stop $P
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo DONE >> $S
