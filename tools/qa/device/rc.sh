# VoltCap QA — recompositions per second while navigating (QaTrace `recomp` lines). Push next to vqa.sh.
#   sh rc.sh NAME [FAULTS]     e.g. sh rc.sh RC off   |   sh rc.sh SNAP snap_colors  (A/B: the colour glide off)
sh vqa.sh hooks on
sh vqa.sh ready || exit 1
am start -n com.shelf.launcher/.MainActivity --es voltcap_test_category ALL >/dev/null 2>&1; sleep 4
am start -n com.shelf.launcher/.MainActivity --es voltcap_test_fault "${2:-off}" >/dev/null 2>&1; sleep 1
logcat -c
sh vqa.sh seq "R R R R R R L L L L R R R R" 300; sleep 3
logcat -d -s VoltcapQA > out/${1}_qa.txt
am start -n com.shelf.launcher/.MainActivity --es voltcap_test_fault off >/dev/null 2>&1
sh vqa.sh hooks off; echo done
