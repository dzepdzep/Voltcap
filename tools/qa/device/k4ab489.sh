# Update 489d / 490 (K4) — A/B of the spines made ahead, cold each time: MODE on (default) or nospines (TestHooks.warmMode).
# sh k4ab489.sh MODE STEPS [LAYOUT] [CATEGORY] -> /sdcard/pc/k4w/ab_MODE_LAYOUT_CATEGORY.txt: spine counts per walk (out, back
# = returning to games already seen, then the same again warm), frame stats over the walks, PSS right after and settled (20 s).
# Preview hooks only; arrows and B (closes the start-up "Who's playing?" keeping the profile) — nothing is launched or saved.
P=com.shelf.launcher; M=$1; N=${2:-80}; L=${3:-CASEFLOW}; C=${4:-SNES}; O=/sdcard/pc/k4w/ab_${M}_${L}_$C.txt; mkdir -p /sdcard/pc/k4w
input keyevent KEYCODE_WAKEUP
am force-stop $P; sleep 2; am start -n $P/.MainActivity > /dev/null 2>&1; sleep 10
input -d 0 keyevent KEYCODE_BUTTON_B; sleep 2
am start -n $P/.MainActivity --es voltcap_test_warm $M > /dev/null 2>&1; sleep 1
dumpsys gfxinfo $P reset > /dev/null
t0=$(date +%s)
sh $(dirname $0)/k4wait488.sh $L $C $N > /data/local/tmp/ab_$M.cnt 2>/dev/null
t1=$(date +%s)
{ echo "mode=$M layout=$L category=$C steps=$N walk_seconds=$((t1 - t0)) (4 walks of $N presses + set-up)"; cat /data/local/tmp/ab_$M.cnt
  dumpsys gfxinfo $P | grep -E 'Total frames|Janky frames:|50th|90th|95th|99th'
  echo "after:"; dumpsys meminfo $P | grep -E 'TOTAL PSS|Graphics:'
  sleep 20; echo "settled 20 s:"; dumpsys meminfo $P | grep -E 'TOTAL PSS|Graphics:'; } > $O
am start -n $P/.MainActivity --es voltcap_test_warm on > /dev/null 2>&1
echo DONE >> $O
