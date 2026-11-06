# Update 489 (D285) — how hard Voltcap's RenderThread works while the screen rests: every STEP s for N steps, the
# RenderThread's CPU % (top, 1 s sample) and the frames drawn since the last step (dumpsys gfxinfo). Read-only.
# sh rtwatch489.sh LABEL N STEP  -> /sdcard/pc/rtw/LABEL.txt
P=com.shelf.launcher; OUT=/sdcard/pc/rtw; mkdir -p $OUT; L=$1; N=${2:-8}; S=${3:-10}
pid=$(pidof $P); [ -n "$pid" ] || { echo "no process"; exit 1; }
f() { dumpsys gfxinfo $P | grep -m1 "Total frames rendered" | awk '{print $4}'; }
dumpsys gfxinfo $P reset > /dev/null; t0=$(date +%s)
i=0; : > $OUT/$L.txt
while [ $i -lt $N ]; do
  sleep $S
  rt=$(top -H -b -n 1 -d 1 -p $pid | grep RenderThread | awk '{print $9}' | head -1)
  fr=$(f); dumpsys gfxinfo $P reset > /dev/null
  echo "$(( $(date +%s) - t0 ))s rt=$rt% frames=$fr" | tee -a $OUT/$L.txt
  i=$((i+1))
done
