# v0.6.66 D131 matrix: layouts x status bar on/off x 4 corners x open/closed. Preview only (nothing saved) except
# shortcuts_open, which the caller wraps in voltcap_test_snapshot save / restore.
# usage: sh d131.sh TAG "DISCS ARCTIC" [extra]   (extra e.g. "|dock=TOP")
P=com.shelf.launcher; TAG=$1; LAYS=$2; EX=$3
OUT=/sdcard/d131/$TAG; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
A --ez voltcap_test_preview true; sleep 2
for lay in $LAYS; do
  A --es voltcap_test_layout $lay; sleep 4
  for st in status nostatus; do
    for c in TOP_LEFT TOP_RIGHT BOTTOM_LEFT BOTTOM_RIGHT; do
      for o in true false; do
        A --es voltcap_test_pref shortcuts_open=$o; sleep 1
        A --es voltcap_test_shortcuts "$c|$st$EX"; sleep 3
        screencap -p $OUT/${lay}_${st}_${c}_$o.png
      done
    done
  done
done
echo done > $OUT/DONE
