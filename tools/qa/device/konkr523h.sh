# Batch 523 (build 523h) on the KONKR (GT78), isolated QA app ONLY: Save Vault looks before / after (fixture saves of games
# here, not here with a recorded look, not here with none), Smart App Artwork on apps without art, deleted games leaving the
# list, then the scroll timing again (before = konkr523g on 523f) and the pull-out recordings. Real Voltcap never touched.
export QA_FIX=true
D=/data/local/tmp; S=/sdcard/pc/konkr523h.txt
echo "start $(date +%T) real=$(pidof com.shelf.launcher)" > $S
sh $D/vault523.sh kv523h > /dev/null 2>&1; echo "vault done $(date +%T)" >> $S
sh $D/appart523.sh ka523h "CLASSIC TILES GRID HERO HERO_TILES CINEMA DASH LIVE CHAN RESUME" "Obtainium RetroArch Files" > /dev/null 2>&1; echo "appart done $(date +%T)" >> $S
sh $D/deleted523.sh kx523h > /dev/null 2>&1; echo "deleted done $(date +%T)" >> $S
for L in CASEFLOW SHELF STACKS COVERDETAILS HERO; do sh $D/perf514.sh k523h$L $L > /dev/null 2>&1; echo "perf $L done $(date +%T)" >> $S; done
sh $D/motion523.sh km523h > /dev/null 2>&1; echo "motion done $(date +%T)" >> $S
am force-stop com.shelf.launcher.qa
echo "real=$(pidof com.shelf.launcher) fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')" >> $S; echo DONE >> $S
