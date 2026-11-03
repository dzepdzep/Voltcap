# Update 462b: the comparison sheet — each subject in Inspect at the same size: front, 45, 75, 90, back (yaw 180).
# usage: sh sheet462b.sh OUTDIR "Name|shell|@" ...   ("@" becomes "yaw|pitch"; extra fields after it: art file | shown name)
P=com.shelf.launcher; OUT=$1; shift; rm -rf $OUT; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
A --ez voltcap_test_nolaunch true; sleep 1
n=0
for subj in "$@"; do
  n=$((n+1))
  for y in 0 45 75 90 180; do
    input keyevent 224
    s=$(echo "$subj" | sed "s/@/$y|0/")
    A --es voltcap_test_inspect "$s"; sleep 4
    screencap -p $OUT/s${n}_y${y}.png
    input keyevent 97; sleep 1; input keyevent 97; sleep 1
  done
  echo "s$n $subj" >> $OUT/subjects.txt
done
tar cf $OUT.tar -C $OUT .
echo done > $OUT/DONE
