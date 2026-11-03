# Update 463: one subject (line N of LIST) of the comparison sheet: front, 45, 75, 90, back. usage: sh one463.sh OUT LIST N
P=com.shelf.launcher; OUT=$1; LIST=$2; n=$3; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
subj=$(sed -n "${n}p" $LIST)
[ $n = 1 ] && A --ez voltcap_test_nolaunch true
# (the game's own console category first: Inspect looks the game up in the shown list)
name=$(echo "$subj" | cut -d'|' -f1); input keyevent 97; sleep 1; A --es voltcap_test_game "$name"; sleep 4
for y in 0 45 75 90 180; do
  input keyevent KEYCODE_WAKEUP
  s=$(echo "$subj" | sed "s/@/$y|0/")
  A --es voltcap_test_inspect "$s"; sleep 4
  screencap -p $OUT/s${n}_y${y}.png
  input keyevent 97; sleep 1; input keyevent 97; sleep 1
done
echo "s$n $subj" >> $OUT/subjects.txt
