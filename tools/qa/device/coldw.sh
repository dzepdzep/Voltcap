# VoltCap QA (pass 5) — cold start time, N times: force-stop, `am start -W` (TotalTime = to the first frame), then B keeps
# the profile on "Who's playing?". No settings touched.   sh coldw.sh RUN 4 → out/RUN_cold.txt
n=$1; k=${2:-4}; : > out/${n}_cold.txt
for i in $(seq 1 $k); do
  am force-stop com.shelf.launcher; sleep 2
  am start -W -n com.shelf.launcher/.MainActivity | grep -E "TotalTime|WaitTime" | tr '\n' ' ' >> out/${n}_cold.txt; echo >> out/${n}_cold.txt
  sleep 6; sh vqa.sh seq "B" 300 > /dev/null; sleep 2
done
cat out/${n}_cold.txt
