# Sprint 525 (owner: "a recording must not count as complete merely because the file exists") - a screen recorder that
# notices when Android's screenrecord stops early. Source it:  . ./rec.sh;  rec_start OUT/NAME SECONDS;  ...;  rec_stop
# - screenrecord runs in segments: if it exits before rec_stop / its time, that is logged (EARLY t=<s> exit=<code>) with
#   the recorder's own output and the system log lines about it, and a new segment NAME_p2.mp4, _p3 ... starts at once,
#   so the run is still covered (the gap is logged);
# - the recorder and its watcher run in their own session (setsid): on the Flip 2 a recording and its watcher in the test
#   script's session both ended ~2.0 s in, several times (12 Nov), with nothing logged - whatever ends that session no
#   longer takes the recorder with it, and a watcher that is gone anyway is reported by rec_stop (LOST + the system log);
# - REC_SEG=N (seconds): record in back-to-back segments of at most N s, each a fresh recorder - on the Flip 2 screenrecord
#   sometimes keeps running but gets no more frames (12 Nov: files ending 2-2.5 s in while the screen kept changing), so a
#   stall then loses at most one segment; recheck.py checks each segment;
# - NAME.rec.txt lists every segment with its start, stop, exit code and why it ended (time / stop / EARLY);
# - screenrecord writes a frame only when the screen changes: a settled screen makes a file shorter than the run; rec_stop
#   keeps the screen at the stop (NAME.end.png) so recheck.py can tell a static tail from a recorder that stopped;
# - tools/qa/tools/recheck.py then checks each file's real duration, frames and decoding against that log.
# Never wakes the handheld, changes no setting; only screenrecord (shell user) is started or stopped.
rec_start() {
  REC_BASE=$1; REC_SECS=${2:-20}; REC_LOG=$REC_BASE.rec.txt; REC_STOPF=$REC_BASE.stop; rm -f $REC_STOPF $REC_LOG ${REC_BASE}*.mp4
  echo "rec $REC_BASE limit=${REC_SECS}s bitrate=${REC_BITRATE:-12000000} start=$(date +%s.%N | cut -c1-14)" > $REC_LOG
  cat > $REC_BASE.loop.sh <<LOOP
t0=\$(date +%s); k=1
while :; do
  [ \$k = 1 ] && f=$REC_BASE.mp4 || f=${REC_BASE}_p\$k.mp4
  left=\$(($REC_SECS - (\$(date +%s) - t0))); [ \$left -lt 1 ] && break
  lim=\$left; [ ${REC_SEG:-0} -gt 0 ] && [ ${REC_SEG:-0} -lt \$lim ] && lim=${REC_SEG:-0}
  s=\$(date +%s.%N | cut -c1-14); s0=\$(date +%s)
  screenrecord --bit-rate ${REC_BITRATE:-12000000} --time-limit \$lim \$f > $REC_BASE.p\$k.out 2>&1 & p=\$!
  echo \$p > $REC_BASE.pid
  wait \$p; code=\$?; e=\$(date +%s.%N | cut -c1-14)
  if [ -f $REC_STOPF ]; then why=stop; elif [ \$(( \$(date +%s) - s0 )) -ge \$((lim - 1)) ]; then why=time; else why=EARLY; fi
  echo "seg \$k file=\$(basename \$f) start=\$s end=\$e exit=\$code why=\$why" >> $REC_LOG
  [ \$why = stop ] && break
  if [ \$why = EARLY ]; then
    echo "EARLY t=\$(( \$(date +%s) - t0 ))s exit=\$code out=\$(tr '\n' ' ' < $REC_BASE.p\$k.out | cut -c1-160)" >> $REC_LOG
    logcat -d -t 400 2>/dev/null | grep -iE "screenrecord|ScreenRecord|MediaCodec|ACodec|C2.*enc|VirtualDisplay" | tail -12 >> $REC_LOG
  fi
  k=\$((k + 1)); [ \$k -gt 40 ] && { echo "GIVE-UP after 40 segments" >> $REC_LOG; break; }
done
echo "done" >> $REC_LOG
LOOP
  setsid sh $REC_BASE.loop.sh > /dev/null 2>&1 < /dev/null &
  sleep 0.6
}
rec_stop() {
  # (the screen as it is at the stop: screenrecord writes frames only when the screen changes, so a recording of a screen
  # that settled ends at its last change - recheck.py tells that static tail from a recorder that stopped, by this picture)
  screencap -p $REC_BASE.end.png 2>/dev/null
  touch $REC_STOPF; p=$(cat $REC_BASE.pid 2>/dev/null); [ -n "$p" ] && kill -INT $p 2>/dev/null
  i=0; while ! grep -q '^done' $REC_LOG 2>/dev/null && [ $i -lt 40 ]; do sleep 0.25; i=$((i + 1)); done
  if ! grep -q '^done' $REC_LOG 2>/dev/null; then
    echo "LOST: the recorder's watcher ended without a word (pid ${p:-?} $(kill -0 ${p:-0} 2>/dev/null && echo alive || echo gone))" >> $REC_LOG
    logcat -d -b all -t 3000 2>/dev/null | grep -iE "screenrecord|sigkill|killing|lowmemorykiller|lmkd|freez" | tail -20 >> $REC_LOG
  fi
  rm -f $REC_BASE.pid $REC_STOPF $REC_BASE.p*.out $REC_BASE.loop.sh
}
