# stacks461.sh OUT — D137 Case stacks, from a cold start: Voltcap restarted, profile picker closed (B), Case stacks preview on
# Advance Wars 2 (GBA); 2 sets x 3 runs of 20 RIGHT presses at 150 ms (gfxinfo), memory after each set, pop-in shots.
O=$1; rm -f $O; P=com.shelf.launcher
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
input keyevent 224; am force-stop $P; sleep 1; A; sleep 9; input keyevent 224; input keyevent 97; sleep 2
A --ez voltcap_test_preview true --es voltcap_test_layout STACKS --es voltcap_test_game "Advance Wars 2"; sleep 8
mem() { dumpsys meminfo $P | grep -E "TOTAL PSS|Graphics:|Native Heap:" | tr -s ' ' | tr '\n' ' '; }
echo "START $(mem)" >> $O
for set in A B; do for r in 1 2 3; do input keyevent 224; dumpsys gfxinfo $P reset >/dev/null
  for i in $(seq 20); do input keyevent 22; sleep 0.15; done; sleep 1
  echo "$set$r $(dumpsys gfxinfo $P | grep -E 'Janky frames:|90th percentile|95th percentile' | tr '\n' ' ')" >> $O
  for i in $(seq 20); do input keyevent 21; sleep 0.05; done; sleep 2; done
  echo "MEM$set $(mem)" >> $O; done
for n in 1 2 3; do input keyevent 224; for i in 1 2 3 4; do input keyevent 22; sleep 0.08; done; screencap -p ${O%.txt}_pop_$n.png; sleep 1.5; done
echo DONE >> $O
