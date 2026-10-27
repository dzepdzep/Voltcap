# VoltCap QA — try to reproduce "the 3D cartridge carousel crash": the Voltcap row as a carousel, the Cartridges look
# with 3D cartridges turning, then fast moves and category switches. Settings snapshot around it; preview only.
#   sh crash3d.sh RUN [VIEW]     VIEW = TUCKED | FLIP | WHEEL | DECK | FLAT | POP   → out/RUN_crash.txt
run=$1; view=${2:-TUCKED}; o=out/${run}_crash.txt
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "crash3d ABORTED"; exit 1; }
A="am start -n com.shelf.launcher/.MainActivity"
$A --es voltcap_test_snapshot save > /dev/null 2>&1; sleep 1
$A --ez voltcap_test_preview true --es voltcap_test_layout CLASSIC > /dev/null 2>&1; sleep 3
for kv in carousel_layouts=CLASSIC carousel_view=$view model_finish=REAL model_turn=true model_reflect=true; do
  $A --es voltcap_test_pref "$kv" > /dev/null 2>&1; sleep 0.4
done
$A --es voltcap_test_look CARTRIDGE_DISC > /dev/null 2>&1; sleep 2
pid0=$(pidof com.shelf.launcher); logcat -c; : > $o
IFS_OLD=$IFS; IFS=,
for c in ${CATS:-GBC,SNES,GBA,GB,ALL}; do
  IFS=$IFS_OLD
  $A --es voltcap_test_category "$c" > /dev/null 2>&1; sleep 2
  screencap -p "out/${run}_$(echo $c | tr -d ' ').png"
  sh vqa.sh seq "R R R R R R R R R R R R L L L L L L R2 R2 L2 L2" 60 > /dev/null
  echo "$c pid=$(pidof com.shelf.launcher)" >> $o
  IFS=,
done
IFS=$IFS_OLD
pid1=$(pidof com.shelf.launcher)
echo "pid before=$pid0 after=$pid1" >> $o
logcat -d -b crash >> $o 2>/dev/null
logcat -d | grep -E "FATAL|AndroidRuntime|ANR in|Fatal signal|OutOfMemory" | head -20 >> $o
$A --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1
$A --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200
cat $o
