#!/system/bin/sh
# Batch 520: one unattended hardware gate for v0.6.70 on any USB handheld — isolated Voltcap QA app ONLY.
# Runs, in order: controller contract on the changed layouts (ctl516), flat / wide art (q520), Case stacks + Game shelf frame
# pacing (perf514), memory soak (mem520, 3 rounds). Every part snapshots and restores the QA app's settings; launches stay
# blocked; the real Voltcap is never touched (no HOME press). Needs the four scripts in /data/local/tmp (CR stripped).
#   sh hwgate520.sh TAG          → <QA files>/TAG_* folders + perf514/ logs; ~35 min
T=$1; D=/data/local/tmp
echo "hwgate520 $T start $(date +%T) $(getprop ro.product.model) $(getprop ro.serialno) real=$(pidof com.shelf.launcher)" > $D/hwgate520_$T.txt
sh $D/ctl516u.sh ${T}_ctl "CLASSIC TILES SHELF STACKS COVERDETAILS CASEFLOW GRID HERO_TILES CINEMA BOX STORAGE FILESELECT CHAN"
sh $D/q520bu.sh ${T}_art
sh $D/perf514u.sh ${T}_stacks STACKS
sh $D/perf514u.sh ${T}_shelf SHELF
sh $D/mem520u.sh ${T}_mem 3
echo "hwgate520 $T end $(date +%T) real=$(pidof com.shelf.launcher)" >> $D/hwgate520_$T.txt
