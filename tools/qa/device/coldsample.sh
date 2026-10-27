# VoltCap QA — cold start + first category switches with QaSampler on (main-thread stalls and picture decodes with
# their stacks: D24 / D25). Leaves the hooks and the sampler file off afterwards.
F=/sdcard/Android/data/com.shelf.launcher/files
n=${1:-CS}
sh vqa.sh hooks on > /dev/null; touch $F/voltcap_test_sampler
am force-stop com.shelf.launcher; sleep 1; logcat -c
am start -n com.shelf.launcher/.MainActivity >/dev/null 2>&1; sleep 8
sh vqa.sh seq "B" 200; sleep 2
sh vqa.sh seq "R2 W1200 R2 W1200 R2 W1500 L2 W800 L2 W800 L2 W800" 300; sleep 2
logcat -d -s VoltcapQA > out/${n}_qa.txt
rm -f $F/voltcap_test_sampler; sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200
echo "coldsample $n: $(grep -c ' stall ' out/${n}_qa.txt) stalls, $(grep -c ' decode ' out/${n}_qa.txt) decodes"
