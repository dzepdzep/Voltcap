#!/system/bin/sh
# Batch 521: hardware gate for any USB handheld - isolated Voltcap QA app ONLY (controller contract on 16 layouts, flat / wide
# art, Tile home placement). Each part snapshots + restores the QA settings; launches blocked; no HOME press, no A in R1.
#   nohup sh gate521.sh TAG &   -> /data/local/tmp/gate521_TAG.txt + <QA files>/TAG_* ; ~15 min
T=$1; D=/data/local/tmp
echo "gate521 $T start $(date +%T) $(getprop ro.product.model) $(getprop ro.serialno) real=$(pidof com.shelf.launcher)" > $D/gate521_$T.txt
sh $D/ctl516.sh ${T}_ctl "CLASSIC TILES SHELF STACKS COVERDETAILS CASEFLOW GRID HERO_TILES CINEMA BOX STORAGE FILESELECT CHAN HERO TV CROSS"
sh $D/q520b.sh ${T}_art
sh $D/tile521.sh ${T}_tile
echo "gate521 $T end $(date +%T) real=$(pidof com.shelf.launcher)" >> $D/gate521_$T.txt
