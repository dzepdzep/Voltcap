# Update 491 (R0/R1/R2 QA) — settings-safe helpers. Never presses A on a tile; taps/holds are done by the caller after a
# screenshot shows the focus. Files land in /sdcard/pc/TAG.
#   sh r1qa491.sh TAG base     -> snapshot save, copy of every snapshot file to /sdcard/pc/TAG/base/
#   sh r1qa491.sh TAG now N    -> snapshot save again, copy to /sdcard/pc/TAG/N/ and diff against base (prints changed keys)
#   sh r1qa491.sh TAG back     -> base files back into place, snapshot restore (caller force-stops + restarts)
P=com.shelf.launcher; TAG=$1; OUT=/sdcard/pc/$TAG; F=/sdcard/Android/data/$P/files
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
cp_snap() { mkdir -p $1; for f in $F/voltcap_test_snapshot*; do cp $f $1/; done; }
keys() { tr ',' '\n' < $1 | tr -d '{}' ; }
case $2 in
  base) mkdir -p $OUT; A --es voltcap_test_snapshot save; sleep 2; cp_snap $OUT/base; ls $OUT/base; md5sum $OUT/base/* | cut -c1-12 ;;
  now)  A --es voltcap_test_snapshot save; sleep 2; cp_snap $OUT/$3
        for f in $OUT/base/*.json; do b=$(basename $f); keys $f | sort > /data/local/tmp/a.txt; keys $OUT/$3/$b | sort > /data/local/tmp/b.txt
          d=$(diff /data/local/tmp/a.txt /data/local/tmp/b.txt | grep -E '^[-+][^-+]'); [ -n "$d" ] && { echo "== $b"; echo "$d"; }; done
        cmp -s $OUT/base/voltcap_test_snapshot_categories.txt $OUT/$3/voltcap_test_snapshot_categories.txt && echo "categories same" || echo "categories DIFF"
        echo "diff done" ;;
  back) for f in $OUT/base/*; do cp $f $F/; done; A --es voltcap_test_snapshot restore; sleep 2; echo restored ;;
esac
