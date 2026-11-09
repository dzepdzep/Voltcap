#!/system/bin/sh
# Update 513: a longer navigation session in the QA app — memory and responsiveness over time. Per round: 4 layouts x 60
# D-pad steps (0.12 s apart) + R2 / L2 (category) presses + A / B; after each round PSS (KB), Java heap, graphics, and the frame
# stats of the round (janky %, p90). sh mem513.sh TAG [ROUNDS]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; T=$1; R=${2:-5}
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
k() { f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) ;; *) echo "$T ABORT focus $f"; exit 2;; esac; input keyevent $1; sleep ${2:-0.12}; }
mem() { M=$(dumpsys meminfo $P); echo "$T $1 pss=$(echo "$M" | grep 'TOTAL PSS' | awk '{print $3}') java=$(echo "$M" | grep -m1 'Java Heap:' | awk '{print $3}') gfx=$(echo "$M" | grep -m1 'Graphics:' | awk '{print $2}') native=$(echo "$M" | grep -m1 'Native Heap:' | awk '{print $3}')"; }
A --ez voltcap_test_nolaunch true; sleep 2; grep -q "^PICKER $(pidof $P)" $F/voltcap_qa_screen && k 97 1.5
mem start
i=1; while [ $i -le $R ]; do
  dumpsys gfxinfo $P reset >/dev/null
  for LY in SHELF TILES STACKS COVERDETAILS; do
    A --es voltcap_test_layout $LY; sleep 2.5
    n=0; while [ $n -lt 30 ]; do k 22; n=$((n+1)); done; n=0; while [ $n -lt 30 ]; do k 21; n=$((n+1)); done
    k 105 1.2; k 104 1.2; k 96 1.2; k 97 1.0
  done
  G=$(dumpsys gfxinfo $P); echo "$T round$i frames=$(echo "$G" | grep -m1 'Total frames' | awk '{print $4}') janky=$(echo "$G" | grep -m1 'Janky frames:' | awk '{print $4}') p90=$(echo "$G" | grep -m1 '90th percentile' | awk '{print $3}') p99=$(echo "$G" | grep -m1 '99th percentile' | awk '{print $3}')"
  mem round$i; i=$((i+1))
done
sleep 20; mem rest20s
