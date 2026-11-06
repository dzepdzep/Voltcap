# Update 489d (K4) — A/B of the spines made ahead, cold each time: MODE on (default) or nospines (TestHooks.warmMode).
# sh k4ab489.sh MODE STEPS -> /sdcard/pc/k4w/ab_MODE.txt (spine counts, frame stats over the walks, PSS after).
# Preview hooks only; arrows and B (closes the start-up "Who's playing?" keeping the profile) — nothing is launched or saved.
P=com.shelf.launcher; M=$1; N=${2:-80}; O=/sdcard/pc/k4w/ab_$M.txt; mkdir -p /sdcard/pc/k4w
am force-stop $P; sleep 2; am start -n $P/.MainActivity > /dev/null 2>&1; sleep 10
input -d 0 keyevent KEYCODE_BUTTON_B; sleep 2
am start -n $P/.MainActivity --es voltcap_test_warm $M > /dev/null 2>&1; sleep 1
dumpsys gfxinfo $P reset > /dev/null
sh $(dirname $0)/k4wait488.sh CASEFLOW SNES $N > /data/local/tmp/ab_$M.cnt 2>/dev/null
{ echo "mode=$M steps=$N"; cat /data/local/tmp/ab_$M.cnt
  dumpsys gfxinfo $P | grep -E 'Total frames|Janky frames:|50th|90th|99th'
  dumpsys meminfo $P | grep -E 'TOTAL PSS|Graphics:'; } > $O
am start -n $P/.MainActivity --es voltcap_test_warm on > /dev/null 2>&1
echo DONE >> $O
