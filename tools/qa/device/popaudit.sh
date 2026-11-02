# v0.6.66 popup audit (preview only; popup_style_by_layout is wrapped in voltcap_test_snapshot save / restore by the caller).
# usage: sh popaudit.sh TAG "CHAN STACKS ..." "OWN BANNER ..." [size]
# per layout x style: before.png, a recording (A open, B close, then rapid A B A B at 250 ms), after.png, VoltcapQA log,
# and the foreground app (a game must never start). v0.6.66: launches are blocked + logged (voltcap_test_nolaunch); launch.txt lists runs that tried.
P=com.shelf.launcher; TAG=$1; LAYS=$2; STYLES=$3; SZ=${4:-960x540}
V=/data/local/tmp/vqa; OUT=/sdcard/pa/$TAG; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 2
for lay in $LAYS; do
  A --es voltcap_test_layout $lay; sleep 4
  for st in $STYLES; do
    n=${lay}_$st
    A --es voltcap_test_pref "popup_style_by_layout=$lay=$st"; sleep 1.5
    screencap -p $OUT/${n}_before.png
    logcat -c
    (screenrecord --time-limit 7 --size $SZ --bit-rate 8000000 $OUT/$n.mp4 > /dev/null 2>&1 &); sleep 0.7
    sh $V/vqa.sh seq "A" 100 > /dev/null; sleep 1.6
    screencap -p $OUT/${n}_open.png
    sh $V/vqa.sh seq "B" 100 > /dev/null; sleep 1.3
    sh $V/vqa.sh seq "A B A B" 250 > /dev/null; sleep 2.2
    screencap -p $OUT/${n}_after.png
    logcat -d -s VoltcapQA VoltcapTest > $OUT/${n}_log.txt
    grep -q "LAUNCH blocked" $OUT/${n}_log.txt && echo "$n LAUNCH-ATTEMPT" >> $OUT/launch.txt
    fg=$(dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity")
    echo "$n $fg" >> $OUT/fg.txt
    case "$fg" in *com.shelf.launcher*) ;; *) echo "STOP: not Voltcap in front after $n" >> $OUT/fg.txt; echo done > $OUT/DONE; exit 1;; esac
  done
done
A --ez voltcap_test_nolaunch false
echo done > $OUT/DONE
