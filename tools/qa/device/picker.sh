# VoltCap QA — which screen is showing? Prints PICKER, HOME or UNKNOWN (never anything else).
# Update 447 (D144): "Who's playing?" has six looks now, so the screen is no longer guessed from its colours. Voltcap (with the
# test hooks file) writes <external files>/voltcap_qa_screen = "PICKER <pid>" while a picker is up, "HOME <pid>" after it.
#   PICKER  = the display is awake, Voltcap is in front, and its live process says PICKER
#   HOME    = the same, it says HOME, AND the screen is lit (a black capture is never HOME)
#   UNKNOWN = display asleep / Voltcap not in front / a mark that isn't PICKER or HOME / black, missing, short or malformed capture
# A stale mark (another pid) is never trusted: the old colour check decides, and only while awake with Voltcap in front.
# Without a mark (an older build, hooks off) it falls back to the old check: the deep-blue top-left corner of the Midnight look.
# Host tests: PK_MARK=<file> PK_PID=<pid> PK_AWAKE=1|0 PK_TOP=1|0 PK_RAW=<raw capture> replace the device reads.
P=com.shelf.launcher
MARK=${PK_MARK:-/sdcard/Android/data/$P/files/voltcap_qa_screen}
f=${PK_RAW:-/data/local/tmp/pk.raw}

awake() { [ -n "$PK_AWAKE" ] && { [ "$PK_AWAKE" = 1 ]; return; }; dumpsys power 2>/dev/null | grep -q "mWakefulness=Awake"; }
ontop() { [ -n "$PK_TOP" ] && { [ "$PK_TOP" = 1 ]; return; }
  dumpsys activity activities 2>/dev/null | grep -E "ResumedActivity" | head -1 | grep -q "$P/"; }
livepid() { [ -n "$PK_PID" ] && { echo "$PK_PID"; return; }; pidof $P 2>/dev/null | tr ' ' '\n' | head -1; }

# lit: the capture is whole and not black (5 points). Prints nothing; exit 0 = lit, 1 = black, 2 = bad capture.
lit() {
  [ -n "$PK_RAW" ] || screencap $f 2>/dev/null
  [ -s $f ] || return 2
  w=$(od -An -tu4 -N4 $f | tr -d ' '); h=$(od -An -tu4 -j4 -N4 $f | tr -d ' ')
  [ "$w" -gt 64 ] 2>/dev/null && [ "$h" -gt 64 ] 2>/dev/null || return 2
  sz=$(wc -c < $f | tr -d ' ')
  [ "$sz" -ge $(( 16 + w * h * 4 )) ] 2>/dev/null || return 2
  n=0
  for pt in "$((w / 2)) $((h / 2))" "$((w / 4)) $((h / 4))" "$((w * 3 / 4)) $((h * 3 / 4))" "$((w / 2)) $((h * 4 / 5))" "60 60"; do
    set -- $pt
    set -- $(od -An -tu1 -j $(( 16 + ($2 * w + $1) * 4 )) -N3 $f)
    [ -n "$3" ] || return 2
    [ $(( $1 + $2 + $3 )) -gt 36 ] && n=$((n + 1))
  done
  [ $n -gt 0 ] || return 1
  return 0
}

if [ -s "$MARK" ]; then
  set -- $(cat "$MARK" 2>/dev/null)
  m=$1; mp=$2
  if [ -n "$m" ] && [ -n "$mp" ] && [ "$mp" = "$(livepid)" ]; then
    awake || { echo UNKNOWN; exit 0; }
    ontop || { echo UNKNOWN; exit 0; }
    if [ "$m" = PICKER ]; then echo PICKER; exit 0; fi
    if [ "$m" = HOME ]; then lit && echo HOME || echo UNKNOWN; exit 0; fi
    echo UNKNOWN; exit 0
  fi
  # A mark from another process (Voltcap restarted, or a different build): don't trust it; use the fallback below.
  # (Update 473: but a stale mark never lets the fallback pass a screen the mark itself would have refused: the display
  # must be awake and Voltcap in front, as for a live mark.)
  awake || { echo UNKNOWN; exit 0; }
  ontop || { echo UNKNOWN; exit 0; }
fi

# Fallback (no mark): the old Midnight-blue corner check.
lit; r=$?
[ $r -eq 0 ] || { echo UNKNOWN; exit 0; }
w=$(od -An -tu4 -N4 $f | tr -d ' ')
set -- $(od -An -tu1 -j $(( 16 + (60 * w + 60) * 4 )) -N3 $f)
r=$1; g=$2; b=$3
if [ $b -gt $((r + 25)) ] && [ $b -gt $((g + 15)) ]; then echo PICKER; else echo HOME; fi
