# VoltCap QA — idle home frame cost under a few session-only variations (nothing saved: preview + settings snapshot).
#   sh idle.sh RUN "base perf=LITE pref:background_style=BLACK"   → out/RUN_idle.txt
# Each entry: "base" (as is), "perf=LITE|FULL|AUTO" (voltcap_test_perf), "pref:KEY=VALUE" (voltcap_test_pref).
# Entries apply on top of each other in order; the snapshot restore at the end puts everything back.
run=$1; list=$2; o=out/${run}_idle.txt; : > $o
A="am start -n com.shelf.launcher/.MainActivity"
$A --es voltcap_test_snapshot save > /dev/null 2>&1; sleep 1
$A --ez voltcap_test_preview true > /dev/null 2>&1; sleep 1
for e in $list; do
  case $e in
    base) ;;
    perf=*) $A --es voltcap_test_perf ${e#perf=} > /dev/null 2>&1;;
    pref:*) $A --es voltcap_test_pref "${e#pref:}" > /dev/null 2>&1;;
  esac
  sleep 3; dumpsys gfxinfo com.shelf.launcher reset > /dev/null; sleep 6
  r=$(dumpsys gfxinfo com.shelf.launcher | grep -E "Total frames|^Janky frames:|^90th percentile|^90th gpu" | sed 's/  */ /g' | tr '\n' ' ')
  echo "$e | $r" >> $o
done
$A --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1
$A --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
cat $o
