# Batch 523 (build 523i) on the KONKR (GT78), isolated QA app ONLY: Save Vault further down (the games that aren't here),
# then the scroll timing (before = konkr523h on 523h: the case-top cache is the only change for Coverflow details).
export QA_FIX=true
D=/data/local/tmp; S=/sdcard/pc/konkr523i.txt
echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
sh $D/vault523.sh kv523i "CLASSIC ICONS PS2 MEMORY_CARD SD_CARD FLOPPY CAPSULE" > /dev/null 2>&1; echo "vault done $(date +%T)" >> $S
# (each layout twice: a warm-up run first - the first run after an install measures shader / image warm-up, not the motion)
for R in a b; do for L in COVERDETAILS CASEFLOW SHELF STACKS HERO; do sh $D/perf514.sh k523i$R$L $L > /dev/null 2>&1; echo "perf $R $L done $(date +%T)" >> $S; done; done
am force-stop com.shelf.launcher.qa
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo DONE >> $S
