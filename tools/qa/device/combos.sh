# VoltCap QA - combination matrix (tools/qa/COMBOS.md): realistic input sequences over TWO systems at once, with a verdict
# from tools/combocheck.py (process survived, Voltcap in front, L2/R2 stops == category tiles). Session-only changes; the
# settings snapshot is restored; hooks go off again. Bounded: every scenario is a fixed number of presses.
#   sh combos.sh RUN LAYOUT SCENARIO      -> out/RUN_LAYOUT_SCENARIO.txt (VoltcapQA log) + _a.png / _b.png
# Scenarios:
#   parity     open the category screen (tiles line), close it, 24 x R2 (every stop logged)
#   rapidcat   60 alternating R2 / L2 presses at 60 ms, then settle 2 s
#   screenmash B (open) / B (close) x 8 at 250 ms, then R2 L2 and settle
#   navcat     8 x R, 3 x R2, 4 x L, 3 x L2, 6 x R (selection movement while categories change)
run=$1; lay=$2; sc=$3
PKG=com.shelf.launcher; A="am start -n $PKG/.MainActivity"
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "combos ABORTED (not on home)"; exit 1; }
$A --es voltcap_test_snapshot save > /dev/null 2>&1; sleep 1
$A --ez voltcap_test_preview true --es voltcap_test_layout $lay > /dev/null 2>&1; sleep 4
$A --es voltcap_test_category ALL > /dev/null 2>&1; sleep 3
logcat -c; logcat -b crash -c
pid_before=$(pidof $PKG)
case $sc in
  parity)    sh vqa.sh seq "B" 900 > /dev/null; sleep 2; screencap -p out/${run}_${lay}_${sc}_a.png; sh vqa.sh seq "B" 900 > /dev/null; sleep 1
             sh vqa.sh seq "R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2 R2" 350 > /dev/null;;
  rapidcat)  i=0; while [ $i -lt 30 ]; do sh vqa.sh seq "R2 L2" 60 > /dev/null; i=$((i+1)); done;;
  screenmash) i=0; while [ $i -lt 8 ]; do sh vqa.sh seq "B B" 250 > /dev/null; i=$((i+1)); done; sh vqa.sh seq "R2 L2" 400 > /dev/null;;
  navcat)    sh vqa.sh seq "R R R R R R R R R2 R2 R2 L L L L L2 L2 L2 R R R R R R" 200 > /dev/null;;
  *) echo "unknown scenario $sc"; exit 1;;
esac
sleep 2; screencap -p out/${run}_${lay}_${sc}_b.png
pid_after=$(pidof $PKG)
focus=$(dumpsys window | grep mCurrentFocus | grep -o "$PKG" | head -1)
crashes=$(logcat -b crash -d | grep -c "$PKG")
logcat -d -s VoltcapQA > out/${run}_${lay}_${sc}.txt
echo "COMBO pid_before=$pid_before pid_after=$pid_after crashes=$crashes focus=${focus:-none}" >> out/${run}_${lay}_${sc}.txt
$A --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1
$A --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200; echo "combos $run $lay $sc done"
