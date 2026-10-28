# VoltCap QA — which screen is showing? Prints PICKER, HOME or UNKNOWN (never anything else). "Who's playing?" is deep blue
# at the top-left corner (uiautomator can't read Voltcap's screen on the Thor). Reads 5 pixels of a raw screencap.
#   HOME    = the screen is lit AND the corner is not the picker blue
#   PICKER  = lit and the corner is picker blue
#   UNKNOWN = black / missing / short / malformed capture (display off, locked, screensaver, failed screencap)
# A black screen must never read HOME. PK_RAW=<file> reads an existing capture instead of calling screencap (host tests).
f=${PK_RAW:-/data/local/tmp/pk.raw}
[ -n "$PK_RAW" ] || screencap $f 2>/dev/null
[ -s $f ] || { echo UNKNOWN; exit 0; }
w=$(od -An -tu4 -N4 $f | tr -d ' '); h=$(od -An -tu4 -j4 -N4 $f | tr -d ' ')
[ "$w" -gt 64 ] 2>/dev/null && [ "$h" -gt 64 ] 2>/dev/null || { echo UNKNOWN; exit 0; }
# the file must hold the whole frame (16-byte header + w*h*4)
sz=$(wc -c < $f | tr -d ' ')
[ "$sz" -ge $(( 16 + w * h * 4 )) ] 2>/dev/null || { echo UNKNOWN; exit 0; }
lit=0
for pt in "$((w / 2)) $((h / 2))" "$((w / 4)) $((h / 4))" "$((w * 3 / 4)) $((h * 3 / 4))" "$((w / 2)) $((h * 4 / 5))" "60 60"; do
  set -- $pt
  set -- $(od -An -tu1 -j $(( 16 + ($2 * w + $1) * 4 )) -N3 $f)
  [ -n "$3" ] || { echo UNKNOWN; exit 0; }
  [ $(( $1 + $2 + $3 )) -gt 36 ] && lit=$((lit + 1))
done
[ $lit -eq 0 ] && { echo UNKNOWN; exit 0; }
set -- $(od -An -tu1 -j $(( 16 + (60 * w + 60) * 4 )) -N3 $f)
r=$1; g=$2; b=$3
if [ $b -gt $((r + 25)) ] && [ $b -gt $((g + 15)) ]; then echo PICKER; else echo HOME; fi
