# VoltCap QA — what changes while nobody touches the handheld (D18): churn + frame counts, 50 s untouched on home.
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || exit 1
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_churn true > /dev/null 2>&1; sleep 1
logcat -c; dumpsys gfxinfo com.shelf.launcher reset > /dev/null
sleep 50
logcat -d -s VoltcapQA > out/IC_qa.txt
dumpsys gfxinfo com.shelf.launcher > out/IC_gfx.txt
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_churn false > /dev/null 2>&1
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200; echo idlechurn done
