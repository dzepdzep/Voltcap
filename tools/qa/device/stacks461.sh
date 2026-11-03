# stacks461.sh OUT — D137 Case stacks: 2 sets x 3 runs of 20 RIGHT presses at 150 ms (gfxinfo), PSS after each set,
# plus pop-in frames: 3 screenshots right after quick moves. Assumes Case stacks preview on a GBA game already open.
O=$1; rm -f $O; P=com.shelf.launcher
for set in A B; do for r in 1 2 3; do input keyevent 224; dumpsys gfxinfo $P reset >/dev/null
  for i in $(seq 20); do input keyevent 22; sleep 0.15; done; sleep 1
  echo "$set$r $(dumpsys gfxinfo $P | grep -E 'Janky frames:|90th percentile|95th percentile' | tr '\n' ' ')" >> $O
  for i in $(seq 20); do input keyevent 21; sleep 0.05; done; sleep 2; done
  echo "PSS$set $(dumpsys meminfo $P | grep 'TOTAL PSS' | head -1)" >> $O; done
for n in 1 2 3; do input keyevent 224; for i in 1 2 3 4; do input keyevent 22; sleep 0.08; done; screencap -p /sdcard/q461/pop_$n.png; sleep 1.5; done
echo DONE >> $O
