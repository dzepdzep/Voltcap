# Update 462b: what each GB-family game's piece is drawn from on a real home screen (Pocket Quest + Case flow, cartridge look,
# preview / session only) — screenshots + the VoltcapQA mediasrc lines. usage: sh src462.sh OUTDIR
P=com.shelf.launcher; OUT=${1:-/sdcard/src462}; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
input keyevent 224
A --ez voltcap_test_preview true; sleep 1
A --ez voltcap_test_nolaunch true; sleep 1
for lay in POCKET CASEFLOW; do
  A --es voltcap_test_layout $lay; sleep 4
  A --es voltcap_test_look CARTRIDGE_DISC; sleep 3
  for g in "Silver Version" "Fool" "Prism Version" "Crystal Version" "Joel"; do
    logcat -c
    A --es voltcap_test_game "$g"; sleep 6
    n=$(echo "$g" | tr ' ' '_')
    screencap -p $OUT/${lay}_$n.png
    logcat -d -s VoltcapQA | grep mediasrc | sed 's/.*mediasrc //; s/https[^ ]*//' >> $OUT/${lay}_$n.txt
  done
done
tar cf $OUT.tar -C $OUT .
echo done > $OUT/DONE
