# VoltCap QA — is "Who's playing?" showing? Prints PICKER or HOME. The picker is deep blue at the top-left corner
# (uiautomator can't read Voltcap's screen on the Thor). Reads one pixel of a raw screencap.
screencap /data/local/tmp/pk.raw
w=$(od -An -tu4 -N4 /data/local/tmp/pk.raw | tr -d ' ')
off=$(( 16 + (60 * w + 60) * 4 ))
set -- $(od -An -tu1 -j $off -N3 /data/local/tmp/pk.raw)
r=$1; g=$2; b=$3
if [ $b -gt $((r + 25)) ] && [ $b -gt $((g + 15)) ]; then echo PICKER; else echo HOME; fi
