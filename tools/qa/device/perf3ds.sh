# VoltCap QA — who decodes a picture on the main thread the first time a category shows (D25): cold start, then a
# CPU profile (simpleperf, call graphs) while the 3rd category (Nintendo 3DS here) appears. No hooks needed.
#   sh perf3ds.sh   → out/perf3ds.txt (callers of every BitmapFactory / ImageDecoder sample on Voltcap's main thread)
am force-stop com.shelf.launcher; sleep 1
am start -n com.shelf.launcher/.MainActivity >/dev/null 2>&1; sleep 7
sh vqa.sh seq "B" 200; sleep 2
P=$(pidof com.shelf.launcher)
(simpleperf record -p $P -t $P -g -f 2000 --duration 7 -o /data/local/tmp/vqa/out/perf.data > /dev/null 2>&1 &)
sleep 1
sh vqa.sh seq "R2 W1200 R2 W1200 R2 W1500" 300
sleep 5
simpleperf report -i out/perf.data -g caller --percent-limit 0.5 --sort symbol > out/perf3ds_full.txt 2>/dev/null
grep -n -i -E "decode|BitmapFactory|ImageDecoder" out/perf3ds_full.txt | head -40 > out/perf3ds.txt
echo "perf3ds done: $(wc -l < out/perf3ds_full.txt) lines"
