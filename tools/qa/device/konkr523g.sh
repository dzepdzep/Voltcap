# Batch 523: after konkr523f - the scroll timing again (perf514 now waits for the QA app's focus; the first try aborted on
# a null focus) and the Details-in-window check with DPAD_CENTER. Waits for konkr523f's DONE. QA app only.
export QA_FIX=true
D=/data/local/tmp; S=/sdcard/pc/konkr523g.txt
while ! grep -q '^DONE' /sdcard/pc/konkr523f.txt 2>/dev/null; do sleep 20; done
echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
for L in CASEFLOW SHELF STACKS COVERDETAILS; do sh $D/perf514.sh k523g$L $L > /dev/null 2>&1; echo "perf $L done $(date +%T)" >> $S; done
sh $D/motion523.sh kd523g det > /dev/null 2>&1; echo "det done $(date +%T)" >> $S
am force-stop com.shelf.launcher.qa
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo DONE >> $S
