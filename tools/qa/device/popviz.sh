# v0.6.66 popup visual review (preview only; launches blocked). Per layout x style: home still, a 3 s recording of A (open),
# the settled window, and a recording of B (close). usage: sh popviz.sh TAG "LAYOUTS" "STYLES" [size]
P=com.shelf.launcher; TAG=$1; LAYS=$2; STYLES=$3; SZ=${4:-960x540}
V=/data/local/tmp/vqa; OUT=/sdcard/pv/$TAG; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 2
for lay in $LAYS; do
  A --es voltcap_test_layout $lay; sleep 4
  for st in $STYLES; do
    n=${lay}_$st
    A --es voltcap_test_pref "popup_style_by_layout=$lay=$st"; sleep 2
    screencap -p $OUT/${n}_0home.png
    (screenrecord --time-limit 3 --size $SZ --bit-rate 10000000 $OUT/${n}_open.mp4 > /dev/null 2>&1 &); sleep 0.5
    sh $V/vqa.sh seq "A" 100 > /dev/null; sleep 2.8
    screencap -p $OUT/${n}_1open.png
    (screenrecord --time-limit 2 --size $SZ --bit-rate 10000000 $OUT/${n}_close.mp4 > /dev/null 2>&1 &); sleep 0.5
    sh $V/vqa.sh seq "B" 100 > /dev/null; sleep 2
    screencap -p $OUT/${n}_2closed.png
  done
done
logcat -d -s VoltcapQA | grep "LAUNCH" > $OUT/launch.txt
# (489c, D287: the launch block stays on — only the release build's clean-up removes it)
echo done > $OUT/DONE
