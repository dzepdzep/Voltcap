# VoltCap QA — cold start + category switches under atrace (no hooks). Run in the background, poll for out/NAME.done.
#   (sh tcold.sh T5 > out/t5.log 2>&1 &)   → out/NAMEm.atrace (Voltcap's main thread only) → tools/systrace.py
n=$1; rm -f out/$n.done
am force-stop com.shelf.launcher; sleep 1
sh vqa.sh atrace $n 24
am start -n com.shelf.launcher/.MainActivity >/dev/null 2>&1; sleep 7
sh vqa.sh seq "B" 200; sleep 2
sh vqa.sh seq "R2 W900 R R R R2 W900 R R R2 W900 R R L2 W900 L2 W900 L2 W900 R R R R" 300
sleep 6
P=$(pidof com.shelf.launcher)
grep "tracing_mark_write" out/$n.atrace | grep -E "\-$P " > out/${n}m.atrace
grep -o "B|$P|VC:[A-Za-z]*" out/${n}m.atrace | sort | uniq -c > out/$n.done
rm -f out/$n.atrace
