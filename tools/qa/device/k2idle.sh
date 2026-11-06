# Update 489c (K2 evidence) — waits for the owner's own screensaver timer (no setting changed, no input sent) and takes a
# screenshot every 30 s for up to N minutes, so the screensaver over the real home can be compared between builds.
# setsid sh k2idle.sh TAG [MINUTES] &   -> /sdcard/pc/TAG/sNN.png, log.txt
TAG=$1; M=${2:-11}; OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT
dumpsys package com.shelf.launcher | grep -m1 versionName > $OUT/log.txt
i=0; n=$((M*2))
while [ $i -le $n ]; do
  f=$(printf "%s/s%02d.png" $OUT $i); screencap -p $f
  echo "$i $(date +%T) $(dumpsys window | grep -m1 mCurrentFocus | sed 's/.*{//')" >> $OUT/log.txt
  i=$((i+1)); sleep 30
done
echo DONE >> $OUT/log.txt
