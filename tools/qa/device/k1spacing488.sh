# Update 488 (K1) — game spacing per layout: the home at the default (100 %), the smallest and the largest value, on the
# same game. Preview hooks only (voltcap_test_pref); the caller restores the owner's settings with voltcap_test_snapshot.
# Run detached: setsid sh k1spacing488.sh "GAME" &   Results in /sdcard/pc/k1s, DONE in log.txt.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pc/k1s; rm -rf $OUT; mkdir -p $OUT; GAME=$1
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 1
for c in "CLASSIC row_spacing 300" "TILES tiles_spacing 250" "STACKS stacks_spacing 250" "SHELF shelf_spacing 250" "COVERDETAILS cd_spacing 250"; do
  set -- $c; L=$1; K=$2; MAX=$3
  A --es voltcap_test_layout $L; sleep 3.5
  for v in 100 50 $MAX 100; do
    A --es voltcap_test_pref "$K=$v"; sleep 1.2
    [ -n "$GAME" ] && { A --es voltcap_test_game "$GAME"; sleep 2.5; }
    screencap -p $OUT/${L}_$v.png
    echo "${L}_$v" > $F/qa_clip_req; sleep 1.2; cp $F/qa_clip/${L}_$v.json $OUT/ 2>/dev/null
  done
  echo "$L done" >> $OUT/log.txt
done
echo DONE >> $OUT/log.txt
