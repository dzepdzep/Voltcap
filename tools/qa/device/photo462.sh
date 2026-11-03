# Update 462: the cartridge contact sheet — each subject in Inspect at the same size: front, 45°, 75°, 90°, rear, and the
# Inspect window's own resting pose. usage: sh photo462.sh OUTDIR "Name_part|shell" ...   (test hooks file needed)
P=com.shelf.launcher; OUT=$1; shift; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
A --ez voltcap_test_nolaunch true; sleep 1
n=0
for subj in "$@"; do
  n=$((n+1))
  for pose in 0:0 45:0 75:0 90:0 180:0 -25:12; do
    y=${pose%%:*}; p=${pose#*:}
    input keyevent 224
    A --es voltcap_test_inspect "$subj|$y|$p"; sleep 4
    screencap -p $OUT/s${n}_y${y}.png
    input keyevent 97; sleep 1; input keyevent 97; sleep 1
  done
  echo "s$n $subj" >> $OUT/subjects.txt
done
echo done > $OUT/DONE
