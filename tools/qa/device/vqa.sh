#!/system/bin/sh
# VoltCap QA — on-device helper (runs as adb shell user). Push to /data/local/tmp/vqa/vqa.sh.
# Real controller input through the built-in gamepad's evdev node (same path as pressing the buttons),
# frame stats, screen recording, memory snapshots. Never touches app data, saves or settings.
#
#   sh vqa.sh dev                       find the gamepad node (cached in $D/pad)
#   sh vqa.sh seq "R R R L A B" [gap]   press buttons in order (gap ms, default 120)
#   sh vqa.sh hold R 1500               hold a direction/button
#   sh vqa.sh perf_reset                reset Voltcap's frame stats
#   sh vqa.sh perf_dump NAME            save gfxinfo framestats + summary to out/NAME_*
#   sh vqa.sh rec NAME SECONDS          screen recording to out/NAME.mp4 (background, returns at once)
#   sh vqa.sh rate60 | rate_restore     lock the panel at 60 Hz while recording (each app frame = one video frame)
#   sh vqa.sh mem NAME                  dumpsys meminfo summary to out/NAME_mem.txt
#   sh vqa.sh trace NAME SECONDS        Perfetto trace (gfx, view, input, sched, freq) to out/NAME.pftrace
#   sh vqa.sh stress NAME COUNT GAP     COUNT random-ish D-pad/L1/R1 steps with GAP ms (rapid navigation)
PKG=com.shelf.launcher
D=/data/local/tmp/vqa
OUT=$D/out
mkdir -p $OUT

pad_dev() {
  if [ -s $D/pad ]; then cat $D/pad; return; fi
  for e in /dev/input/event*; do
    if getevent -p $e 2>/dev/null | grep -q "0010  : value"; then
      if getevent -p $e 2>/dev/null | grep -q " 0130 "; then echo $e > $D/pad; echo $e; return; fi
    fi
  done
}

# key codes (linux input): A=0x130 B=0x131 X=0x133 Y=0x134 L1=0x136 R1=0x137 L2=0x138 R2=0x139 SELECT=0x13a START=0x13b HOME=0x13c
code_of() {
  case $1 in
    A) echo 304;; B) echo 305;; X) echo 307;; Y) echo 308;; L1) echo 310;; R1) echo 311;;
    L2) echo 312;; R2) echo 313;; SELECT) echo 314;; START) echo 315;; HOME) echo 316;; L3) echo 317;; R3) echo 318;;
    *) echo "";;
  esac
}

ev() { sendevent $P $1 $2 $3; }
syn() { sendevent $P 0 0 0; }

press() { # $1 name, $2 hold ms (default 40)
  h=${2:-40}
  case $1 in
    U) ev 3 17 -1; syn; sleep_ms $h; ev 3 17 0; syn;;
    D) ev 3 17 1; syn; sleep_ms $h; ev 3 17 0; syn;;
    L) ev 3 16 -1; syn; sleep_ms $h; ev 3 16 0; syn;;
    R) ev 3 16 1; syn; sleep_ms $h; ev 3 16 0; syn;;
    W*) sleep_ms ${1#W};;              # W500 = wait 500 ms
    K*) input keyevent ${1#K};;        # K4 / KKEYCODE_BACK = a system key through input
    *) c=$(code_of $1); [ -z "$c" ] && { echo "unknown key $1"; return; }
       ev 1 $c 1; syn; sleep_ms $h; ev 1 $c 0; syn;;
  esac
}

sleep_ms() { # toybox sleep takes fractions
  s=$(( $1 / 1000 )); ms=$(( $1 % 1000 ))
  sleep $s.$(printf %03d $ms)
}

log_mark() { log -t VoltcapQA "MARK $*"; }

P=$(pad_dev)
cmd=$1; shift
case $cmd in
  dev) echo $P;;
  seq)
    gap=${2:-120}
    log_mark "seq $1"
    for k in $1; do press $k; sleep_ms $gap; done;;
  hold) log_mark "hold $1 $2"; press $1 ${2:-1000};;
  perf_reset) dumpsys gfxinfo $PKG reset > /dev/null; log_mark perf_reset;;
  perf_dump)
    dumpsys gfxinfo $PKG framestats > $OUT/$1_framestats.txt
    dumpsys gfxinfo $PKG | sed -n '/Stats since/,/GPU HISTOGRAM/p' > $OUT/$1_summary.txt
    cat $OUT/$1_summary.txt | head -20;;
  rec)
    s=${2:-10}
    rm -f $OUT/$1.mp4
    (screenrecord --bit-rate 40000000 --time-limit $s $OUT/$1.mp4 > /dev/null 2>&1 &) ;
    sleep 0.6; log_mark "rec $1 $s";;
  rate60)
    settings get system peak_refresh_rate > $D/peak.bak; settings get system min_refresh_rate > $D/min.bak
    settings put system peak_refresh_rate 60.0; settings put system min_refresh_rate 60.0; sleep 0.5
    dumpsys display | grep -m1 -o "renderFrameRate [0-9.]*\|refreshRate=[0-9.]*";;
  rate_restore)
    [ -s $D/peak.bak ] && settings put system peak_refresh_rate $(cat $D/peak.bak)
    [ -s $D/min.bak ] && settings put system min_refresh_rate $(cat $D/min.bak)
    dumpsys display | grep -m1 -o "refreshRate=[0-9.]*";;
  mem)
    dumpsys meminfo $PKG | sed -n '1,40p' > $OUT/$1_mem.txt; grep -E "TOTAL PSS|TOTAL RSS|Graphics|Native Heap|Java Heap|Views|Activities" $OUT/$1_mem.txt;;
  trace)
    s=${2:-10}
    cat > $D/trace.cfg <<EOF
buffers { size_kb: 65536 fill_policy: RING_BUFFER }
data_sources { config { name: "linux.ftrace" ftrace_config {
  ftrace_events: "sched/sched_switch" ftrace_events: "power/cpu_frequency" ftrace_events: "power/suspend_resume"
  atrace_categories: "gfx" atrace_categories: "view" atrace_categories: "input" atrace_categories: "dalvik"
  atrace_categories: "sched" atrace_categories: "freq" atrace_apps: "$PKG" } } }
data_sources { config { name: "android.surfaceflinger.frametimeline" } }
data_sources { config { name: "linux.process_stats" } }
duration_ms: $(( s * 1000 ))
EOF
    rm -f /data/misc/perfetto-traces/$1.pftrace
    (perfetto --txt -c - -o /data/misc/perfetto-traces/$1.pftrace < $D/trace.cfg > $OUT/$1_perfetto.log 2>&1; cp /data/misc/perfetto-traces/$1.pftrace $OUT/ 2>/dev/null) &
    sleep 1; log_mark "trace $1 $s";;
  stress)
    n=${2:-60}; gap=${3:-90}; i=0
    log_mark "stress $1 $n $gap"
    keys="R R R L R R D U R L L L R2 R R L2 L R R R"
    while [ $i -lt $n ]; do
      for k in $keys; do press $k 30; sleep_ms $gap; i=$((i+1)); [ $i -ge $n ] && break; done
    done;;
  suite)
    # A whole measured pass, for before / after (run in the background: sh vqa.sh suite NAME > out/NAME.log 2>&1 &).
    n=$1; NAV="R R R R R R W400 L L L L R2 W800 R R R L2 W800"
    log_mark "suite $n start"
    # Same start every time: All games, first game (TestHooks; needs the voltcap_test_hooks file). A row's last game
    # would make every ▶ an edge bump (an intended shake that frames.py flags as a transient).
    am start -n $PKG/.MainActivity --es voltcap_test_category ALL > /dev/null 2>&1; sleep 6
    sh $0 seq "$NAV" 160; sh $0 stress ${n}_warm 60 70; sleep 2
    sh $0 perf_reset; sleep 5; sh $0 perf_dump ${n}_idleactive > /dev/null
    sleep 32; sh $0 perf_reset; sleep 5; sh $0 perf_dump ${n}_idlecalm > /dev/null
    logcat -c
    for r in 1 2 3; do
      sh $0 perf_reset; [ $r = 1 ] && sh $0 rec ${n}_nav 8
      sh $0 seq "$NAV" 160; sleep 2.5; sh $0 perf_dump ${n}_nav$r > /dev/null
    done
    for r in 1 2; do sh $0 perf_reset; sh $0 stress ${n}_stress$r 80 70; sleep 1; sh $0 perf_dump ${n}_stress$r > /dev/null; done
    sh $0 mem $n > /dev/null
    logcat -d -s VoltcapQA > $OUT/${n}_qa.txt
    log_mark "suite $n done"; echo "suite $n done";;
  *) echo "commands: dev seq hold perf_reset perf_dump rec rate60 rate_restore mem trace stress";;
esac
