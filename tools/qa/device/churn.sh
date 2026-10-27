# VoltCap QA — WHY parts re-compose: snapshot churn + QaInval (`churn` and `inval <part>: Nx <state>` lines) during navigation.
#   sh churn.sh        → out/CH_qa.txt    (opt-in diagnostics, R8: never left on)
sh vqa.sh hooks on
sh vqa.sh ready || exit 1
am start -n com.shelf.launcher/.MainActivity --es voltcap_test_category ALL >/dev/null 2>&1; sleep 4
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_churn true >/dev/null 2>&1; sleep 1
logcat -c
sh vqa.sh seq "R R R R R R L L L L R R R R" 300; sleep 3
logcat -d -s VoltcapQA > out/CH_qa.txt
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_churn false >/dev/null 2>&1
sh vqa.sh hooks off; echo done
