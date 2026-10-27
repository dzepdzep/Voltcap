# VoltCap QA (pass 4) — Mix › Customize consoles: one console's medium set in turn (session only: preview + the settings
# snapshot), a screenshot of that console's category in the Cartridges & discs look for each.
#   sh mixcon.sh RUN CONSOLE "AUTO MEDIUM CASE CASE_MEDIA COVER" [LAYOUT]
run=$1; c=$2; list=$3; lay=${4:-CLASSIC}
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "mixcon ABORTED"; exit 1; }
A="am start -n com.shelf.launcher/.MainActivity"
$A --es voltcap_test_snapshot save > /dev/null 2>&1; sleep 1
$A --ez voltcap_test_preview true --es voltcap_test_layout $lay > /dev/null 2>&1; sleep 3
$A --es voltcap_test_look CARTRIDGE_DISC > /dev/null 2>&1; sleep 2
$A --es voltcap_test_category $c > /dev/null 2>&1; sleep 3
for m in $list; do
  $A --es voltcap_test_pref "mix_consoles={\"$c\":\"$m\"}" > /dev/null 2>&1; sleep 3
  screencap -p out/${run}_${c}_$m.png; echo "$m done"
done
$A --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200; echo "mixcon $run done"
